#!/bin/sh
# Base44 preview entrypoint for the Neon EQ Android project.
# Runs the full CI pipeline (DSP harness + unit tests + APK build),
# then serves a status page with a download link on port 3000.
set -e

PROJECT_DIR="/project"
SERVE_DIR="/serve"
mkdir -p "$SERVE_DIR"

cd "$PROJECT_DIR"

# Write the SDK path for Gradle
echo "sdk.dir=$ANDROID_HOME" > local.properties

# ---- Step 1: Native DSP host self-test (ASan/UBSan) ----
HARNESS_OK="no"
HARNESS_LOG="$SERVE_DIR/harness.log"
{
  echo "=== Native DSP host self-test (ASan/UBSan) ==="
  if g++ -std=c++17 -O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer \
      -I tools/dspharness -I . tools/dspharness/harness.cpp \
      -o /tmp/dsp_harness -lpthread 2>&1; then
    /tmp/dsp_harness 2>&1
    echo "HARNESS_EXIT=$?"
  else
    echo "HARNESS_COMPILE_FAILED"
  fi
} > "$HARNESS_LOG" 2>&1
if grep -q "HARNESS PASS" "$HARNESS_LOG"; then
  HARNESS_OK="yes"
fi

# ---- Step 2: Unit tests ----
TEST_OK="no"
TEST_LOG="$SERVE_DIR/tests.log"
{
  echo "=== Gradle unit tests ==="
  chmod +x gradlew
  ./gradlew testDebugUnitTest --no-daemon --continue 2>&1
  echo "TEST_EXIT=$?"
} > "$TEST_LOG" 2>&1
if grep -q "BUILD SUCCESSFUL" "$TEST_LOG"; then
  TEST_OK="yes"
fi

# ---- Step 3: Build debug APK ----
APK_OK="no"
APK_LOG="$SERVE_DIR/apk.log"
APK_PATH=""
{
  echo "=== Gradle assembleDebug ==="
  ./gradlew assembleDebug --no-daemon -x test 2>&1
  echo "APK_EXIT=$?"
} > "$APK_LOG" 2>&1
if grep -q "BUILD SUCCESSFUL" "$APK_LOG"; then
  APK_OK="yes"
  APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
  if [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$SERVE_DIR/app-debug.apk"
  fi
fi

# ---- Determine version ----
VERSION="3.7.0"
APK_SIZE="—"
if [ -f "$SERVE_DIR/app-debug.apk" ]; then
  APK_SIZE=$(du -h "$SERVE_DIR/app-debug.apk" | cut -f1)
fi

# ---- Generate status page ----
OVERALL="FAILING"
if [ "$HARNESS_OK" = "yes" ] && [ "$TEST_OK" = "yes" ] && [ "$APK_OK" = "yes" ]; then
  OVERALL="PASSING"
fi

gen_status() {
  local icon_ok="✅"
  local icon_no="❌"
  cat <<HTMLEOF
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Neon EQ — Build Status</title>
<style>
  :root { --bg:#0a0a0f; --card:#15151f; --accent:#00e5ff; --accent2:#b388ff; --ok:#00e676; --err:#ff5252; --text:#e0e0e0; --dim:#888; }
  * { box-sizing:border-box; margin:0; padding:0; }
  body { background:var(--bg); color:var(--text); font-family:-apple-system,system-ui,'Segoe UI',Roboto,sans-serif; min-height:100vh; padding:24px; }
  .wrap { max-width:760px; margin:0 auto; }
  h1 { font-size:1.6rem; margin-bottom:4px; background:linear-gradient(90deg,var(--accent),var(--accent2)); -webkit-background-clip:text; -webkit-text-fill-color:transparent; }
  .sub { color:var(--dim); font-size:.9rem; margin-bottom:24px; }
  .badge { display:inline-block; padding:4px 14px; border-radius:20px; font-weight:700; font-size:.85rem; margin-bottom:20px; }
  .badge.pass { background:rgba(0,230,118,.12); color:var(--ok); border:1px solid rgba(0,230,118,.3); }
  .badge.fail { background:rgba(255,82,82,.12); color:var(--err); border:1px solid rgba(255,82,82,.3); }
  .card { background:var(--card); border-radius:14px; padding:18px 20px; margin-bottom:14px; border:1px solid rgba(255,255,255,.06); }
  .card h2 { font-size:1.05rem; margin-bottom:8px; display:flex; align-items:center; gap:8px; }
  .meta { color:var(--dim); font-size:.82rem; margin-top:6px; }
  .log { background:#0d0d14; border-radius:8px; padding:12px; margin-top:10px; font-family:'SF Mono',Consolas,monospace; font-size:.76rem; max-height:240px; overflow:auto; white-space:pre-wrap; color:#aaa; border:1px solid rgba(255,255,255,.05); }
  .dl { display:inline-block; margin-top:10px; padding:10px 22px; border-radius:10px; background:linear-gradient(90deg,var(--accent),var(--accent2)); color:#000; font-weight:700; text-decoration:none; font-size:.9rem; }
  .dl:hover { opacity:.9; }
  .grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
  @media(max-width:560px){ .grid{grid-template-columns:1fr} }
  .stat { color:var(--dim); font-size:.8rem; }
  .stat b { color:var(--text); }
</style>
</head>
<body>
<div class="wrap">
  <h1>🎧 Neon EQ</h1>
  <div class="sub">Android system-wide equalizer · v$VERSION · Base44 build preview</div>
  <span class="badge $([ "$OVERALL" = "PASSING" ] && echo pass || echo fail)">$OVERALL</span>

  <div class="grid">
    <div class="card">
      <h2>$([ "$HARNESS_OK" = "yes" ] && echo "$icon_ok" || echo "$icon_no") DSP Harness</h2>
      <div class="stat">Native self-test · ASan + UBSan</div>
      <details><summary class="meta">View log</summary><div class="log">$(sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g' "$HARNESS_LOG" | tail -40)</div></details>
    </div>
    <div class="card">
      <h2>$([ "$TEST_OK" = "yes" ] && echo "$icon_ok" || echo "$icon_no") Unit Tests</h2>
      <div class="stat">Gradle testDebugUnitTest</div>
      <details><summary class="meta">View log</summary><div class="log">$(sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g' "$TEST_LOG" | tail -40)</div></details>
    </div>
  </div>

  <div class="card">
    <h2>$([ "$APK_OK" = "yes" ] && echo "$icon_ok" || echo "$icon_no") APK Build</h2>
    <div class="stat">assembleDebug · <b>$APK_SIZE</b></div>
HTMLEOF
  if [ "$APK_OK" = "yes" ] && [ -f "$SERVE_DIR/app-debug.apk" ]; then
    echo "    <a class=\"dl\" href=\"/app-debug.apk\" download>⬇ Download app-debug.apk ($APK_SIZE)</a>"
  fi
  cat <<HTMLEOF2
    <details><summary class="meta">View log</summary><div class="log">$(sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g' "$APK_LOG" | tail -40)</div></details>
  </div>

  <div class="card">
    <h2>📱 About</h2>
    <div class="stat">System-wide audio equalizer for Android · 5/10/15/31-band EQ · presets · per-app profiles · native DSP engine (limiter/compressor/convolver) · glass-morphism AMOLED UI</div>
    <div class="meta" style="margin-top:8px">Build: $(date -u '+%Y-%m-%d %H:%M UTC') · Source: conversations/…/android-eq</div>
  </div>
</div>
</body>
</html>
HTMLEOF2
}

gen_status > "$SERVE_DIR/index.html"

echo "=== Build complete: $OVERALL ==="
echo "  Harness: $HARNESS_OK | Tests: $TEST_OK | APK: $APK_OK"

# ---- Serve on port 3000 ----
cd "$SERVE_DIR"
exec python3 -m http.server 3000 --bind 0.0.0.0
