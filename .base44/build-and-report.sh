#!/bin/sh
# Builds the SonicCore / Neon EQ Android app from the bind-mounted working tree
# and publishes a preview page (APK download + verification report) to /dist,
# which the nginx service serves on host port 3000.
#
# Steps mirror .github/workflows/android-apk.yml:
#   1. native DSP host self-test (ASan/UBSan)
#   2. JVM unit tests
#   3. debug APK assembly
#
# The browser preview cannot execute an Android app, so this page is the
# closest thing to "seeing it working": a real, installable debug APK plus the
# measured results of the project's own test suites.
set -u

PROJECT=/workspace/conversations/6a7a961312b12b330076a6ce/android-eq
DIST=/dist
WORK=/tmp/build
SDK=${ANDROID_SDK_ROOT:-/opt/android-sdk}
# gradle.properties pins android.cmake.path to a machine-specific standalone
# CMake (/opt/soniccore-tools/...). CI deletes that line to use the SDK CMake;
# we override the property on the command line instead, leaving the repo file
# untouched so the developer's own setup keeps working.
CMAKE_PROP="-Pandroid.cmake.path=$SDK/cmake/3.22.1"

mkdir -p "$DIST" "$WORK"
# Marks a finished build; the compose healthcheck waits for this file.
rm -f "$DIST/.build-complete"

esc() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }

# ---------------------------------------------------------------- placeholder
cat > "$DIST/index.html" <<'HTML'
<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta http-equiv="refresh" content="8">
<title>SonicCore — building</title>
<style>
  body{margin:0;min-height:100vh;display:grid;place-items:center;background:#05060a;
       color:#e6e9f2;font:16px/1.6 ui-sans-serif,system-ui,-apple-system,Segoe UI,sans-serif}
  .box{max-width:34rem;padding:2rem;text-align:center}
  h1{font-size:1.1rem;letter-spacing:.18em;text-transform:uppercase;color:#22d3ee;margin:0 0 .75rem}
  p{color:#93a0b8;margin:.5rem 0}
  .bar{height:3px;border-radius:2px;background:linear-gradient(90deg,#22d3ee,#a855f7);overflow:hidden;margin-top:1.5rem}
  .bar span{display:block;height:100%;width:38%;background:#05060a;animation:slide 1.4s infinite ease-in-out}
  @keyframes slide{0%{transform:translateX(-100%)}100%{transform:translateX(360%)}}
</style></head>
<body><div class="box">
  <h1>SonicCore · Neon EQ</h1>
  <p>Compiling the Android app from source — first run downloads the SDK and Gradle dependencies.</p>
  <p>This page refreshes automatically.</p>
  <div class="bar"><span></span></div>
</div></body></html>
HTML

cd "$PROJECT" || { echo "project path missing: $PROJECT"; exit 1; }

# ------------------------------------------------- 1. native DSP host self-test
g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer \
    -I tools/dspharness -I . tools/dspharness/harness.cpp \
    -o "$WORK/dsp_harness" -lpthread > "$WORK/harness.log" 2>&1
if [ $? -eq 0 ]; then
  "$WORK/dsp_harness" >> "$WORK/harness.log" 2>&1
  HARNESS_RC=$?
else
  HARNESS_RC=1
fi
case $HARNESS_RC in
  0) HARNESS_LABEL="passed"; HARNESS_CLASS="ok" ;;
  *) HARNESS_LABEL="failed (exit $HARNESS_RC)"; HARNESS_CLASS="bad" ;;
esac

# ------------------------------------------------------------- 2. unit tests
./gradlew testDebugUnitTest --no-daemon --max-workers=2 --continue $CMAKE_PROP \
    > "$WORK/tests.log" 2>&1
TESTS_RC=$?

TESTS_TOTAL=0
TESTS_FAILED=0
for f in "$PROJECT"/app/build/test-results/testDebugUnitTest/*.xml; do
  [ -f "$f" ] || continue
  t=$(grep -o 'tests="[0-9]*"' "$f" | head -1 | tr -dc '0-9')
  x=$(grep -o 'failures="[0-9]*"' "$f" | head -1 | tr -dc '0-9')
  e=$(grep -o 'errors="[0-9]*"' "$f" | head -1 | tr -dc '0-9')
  TESTS_TOTAL=$((TESTS_TOTAL + ${t:-0}))
  TESTS_FAILED=$((TESTS_FAILED + ${x:-0} + ${e:-0}))
done

if [ $TESTS_RC -eq 0 ]; then
  TESTS_LABEL="$TESTS_TOTAL tests passed"; TESTS_CLASS="ok"
else
  TESTS_LABEL="$TESTS_FAILED of $TESTS_TOTAL tests failed"; TESTS_CLASS="bad"
fi

# -------------------------------------------------------------- 3. debug APK
./gradlew assembleDebug --no-daemon --max-workers=2 $CMAKE_PROP \
    > "$WORK/apk.log" 2>&1
APK_RC=$?

APK_SRC="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
APK_BLOCK='<p class="muted">No APK was produced — see the build log below.</p>'
APK_LABEL="not built"
APK_CLASS="bad"
if [ $APK_RC -eq 0 ] && [ -s "$APK_SRC" ]; then
  cp "$APK_SRC" "$DIST/app-debug.apk"
  BYTES=$(wc -c < "$APK_SRC")
  SIZE=$(awk -v b="$BYTES" 'BEGIN{printf "%.1f MB", b/1048576}')
  APK_LABEL="built · $SIZE"
  APK_CLASS="ok"
  APK_BLOCK='<a class="btn" href="app-debug.apk" download>Download app-debug.apk</a>
  <p class="muted">versionName 3.6.0 · minSdk 26 · arm64-v8a + armeabi-v7a · debug signed</p>'
fi

HARNESS_EXCERPT=$(tail -n 18 "$WORK/harness.log" 2>/dev/null | esc)
TESTS_EXCERPT=$(tail -n 22 "$WORK/tests.log" 2>/dev/null | esc)
APK_EXCERPT=$(tail -n 30 "$WORK/apk.log" 2>/dev/null | esc)
STAMP=$(date -u '+%Y-%m-%d %H:%M UTC')

if [ $HARNESS_RC -eq 0 ] && [ $TESTS_RC -eq 0 ] && [ $APK_RC -eq 0 ]; then
  HEADLINE="Build complete"; HEAD_CLASS="ok"
else
  HEADLINE="Build finished with errors"; HEAD_CLASS="bad"
fi

cat > "$DIST/index.html" <<HTML
<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>SonicCore · Neon EQ — Android build</title>
<style>
  :root{color-scheme:dark}
  *{box-sizing:border-box}
  body{margin:0;background:#05060a;color:#e6e9f2;
       font:16px/1.6 ui-sans-serif,system-ui,-apple-system,"Segoe UI",sans-serif}
  .wrap{max-width:52rem;margin:0 auto;padding:3rem 1.25rem 4rem}
  header{border-bottom:1px solid #1b2233;padding-bottom:1.5rem;margin-bottom:2rem}
  h1{margin:0;font-size:1.6rem;letter-spacing:.02em}
  h1 span{color:#22d3ee}
  .sub{color:#8b98b0;margin:.4rem 0 0;font-size:.95rem}
  .notice{border-left:3px solid #a855f7;background:#0b0f19;padding:.9rem 1.1rem;
          border-radius:0 8px 8px 0;margin:0 0 2rem;color:#c7cfdd;font-size:.95rem}
  .grid{display:grid;gap:1rem;grid-template-columns:repeat(auto-fit,minmax(15rem,1fr));margin-bottom:2rem}
  .card{background:#0b0f19;border:1px solid #1b2233;border-radius:12px;padding:1.1rem}
  .card h2{margin:0 0 .5rem;font-size:.72rem;letter-spacing:.14em;text-transform:uppercase;color:#8b98b0}
  .status{font-size:1.05rem;font-weight:600}
  .ok{color:#34d399} .bad{color:#f87171}
  .btn{display:inline-block;margin:.2rem 0 .6rem;padding:.75rem 1.35rem;border-radius:10px;
       background:linear-gradient(135deg,#22d3ee,#a855f7);color:#04060b;font-weight:700;
       text-decoration:none;letter-spacing:.01em}
  .btn:hover{filter:brightness(1.08)}
  .muted{color:#8b98b0;font-size:.85rem;margin:.2rem 0 0}
  ol{padding-left:1.2rem;color:#c7cfdd;font-size:.95rem}
  ol li{margin:.35rem 0}
  details{background:#0b0f19;border:1px solid #1b2233;border-radius:12px;padding:.9rem 1.1rem;margin-bottom:.75rem}
  summary{cursor:pointer;font-size:.9rem;color:#c7cfdd}
  pre{margin:.9rem 0 0;padding:.9rem;background:#05060a;border:1px solid #1b2233;border-radius:8px;
      overflow-x:auto;font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,monospace;color:#9fb0c8}
  footer{color:#5d6b85;font-size:.8rem;border-top:1px solid #1b2233;padding-top:1rem}
</style></head>
<body><div class="wrap">
  <header>
    <h1>SonicCore <span>·</span> Neon EQ</h1>
    <p class="sub">Android audio equalizer — debug build from this repository</p>
  </header>

  <p class="notice">
    This project is a native Android app (Kotlin/Compose + C++ DSP), so it cannot run
    inside a browser. The build below compiles it from source and hands you a real,
    installable debug APK.
  </p>

  <div class="grid">
    <div class="card"><h2>Status</h2>
      <p class="status ${HEAD_CLASS}">${HEADLINE}</p>
      <p class="muted">${STAMP}</p></div>
    <div class="card"><h2>Debug APK</h2>
      <p class="status ${APK_CLASS}">${APK_LABEL}</p></div>
    <div class="card"><h2>Native DSP self-test</h2>
      <p class="status ${HARNESS_CLASS}">${HARNESS_LABEL}</p>
      <p class="muted">ASan + UBSan host harness over the real engine</p></div>
    <div class="card"><h2>JVM unit tests</h2>
      <p class="status ${TESTS_CLASS}">${TESTS_LABEL}</p></div>
  </div>

  <div class="card" style="margin-bottom:2rem">
    <h2>Get the app</h2>
    ${APK_BLOCK}
    <ol>
      <li>Download the APK to your Android phone (Android 8.0 / API 26 or newer).</li>
      <li>Allow installing from unknown sources when prompted.</li>
      <li>Install, open SonicCore, and grant the audio / notification permissions it asks for.</li>
      <li>Playback capture additionally needs a MediaProjection grant and a capture-eligible source app (Android 10+).</li>
    </ol>
  </div>

  <details><summary>Native DSP self-test log</summary><pre>${HARNESS_EXCERPT}</pre></details>
  <details><summary>Unit test log</summary><pre>${TESTS_EXCERPT}</pre></details>
  <details><summary>APK build log</summary><pre>${APK_EXCERPT}</pre></details>

  <footer>Rebuild after changing source: <code>docker compose -f docker-compose.base44.yml restart apk</code></footer>
</div></body></html>
HTML

echo "preview page written to $DIST/index.html (harness=$HARNESS_RC tests=$TESTS_RC apk=$APK_RC)"

# Mark the build as finished (success or failure) and stay alive: the service is
# a long-running worker so the preview host and its healthcheck keep running.
# Rebuild after a source edit with: docker compose ... restart apk
printf 'harness=%s tests=%s apk=%s\n' "$HARNESS_RC" "$TESTS_RC" "$APK_RC" > "$DIST/.build-complete"
exec sleep infinity
