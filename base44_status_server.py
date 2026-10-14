#!/usr/bin/env python3
"""Base44 status server for the NeonEQ Android project.

This is an Android audio EQ app (Kotlin + C++ JNI) that cannot run in a
browser.  Instead of a live app preview, this server compiles and runs the
native DSP self-test harness (the same g++ command the CI uses) and serves
an HTML status page on port 3000 so the build is visible in the preview.

The harness is re-run automatically when the C++ source changes, so editing
neondsp.cpp or harness.cpp and reloading the page shows fresh results.
"""

import hashlib
import html
import http.server
import os
import subprocess
import threading

PORT = int(os.environ.get("PORT", "3000"))
PROJECT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "conversations", "6a7a961312b12b330076a6ce", "android-eq",
)
HARNESS_SRC = os.path.join(PROJECT_DIR, "tools", "dspharness", "harness.cpp")
DSP_SRC = os.path.join(PROJECT_DIR, "app", "src", "main", "cpp", "neondsp.cpp")
JNI_HEADER = os.path.join(PROJECT_DIR, "tools", "dspharness", "jni.h")
BUILD_DIR = "/tmp/base44_build"
HARNESS_BIN = os.path.join(BUILD_DIR, "dsp_harness")

_lock = threading.Lock()
_cache = {"hash": None, "compile_ok": False, "compile_output": "",
          "run_ok": False, "run_output": "", "sections": []}


def _source_hash():
    """Return a hash of all source files that affect the harness build."""
    h = hashlib.sha256()
    for path in (HARNESS_SRC, DSP_SRC, JNI_HEADER):
        try:
            with open(path, "rb") as f:
                h.update(f.read())
        except OSError:
            h.update(b"missing")
    return h.hexdigest()


def _parse_sections(output):
    """Extract [N] section titles and their pass/fail status from harness output."""
    sections = []
    current = None
    fail_count = 0
    for line in output.splitlines():
        stripped = line.strip()
        if stripped.startswith("[") and "]" in stripped:
            idx = stripped.index("]")
            title = stripped[:idx + 1]
            desc = stripped[idx + 1:].strip()
            current = {"title": title, "desc": desc, "checks": [], "failed": 0}
            sections.append(current)
        elif current is not None and (stripped.startswith("ok:") or stripped.startswith("FAIL:")):
            is_fail = stripped.startswith("FAIL:")
            current["checks"].append({
                "ok": not is_fail,
                "msg": stripped[4:].strip() if is_fail else stripped[4:].strip(),
            })
            if is_fail:
                current["failed"] += 1
                fail_count += 1
    return sections, fail_count


def _run_harness():
    """Compile and run the DSP harness, updating the cache."""
    os.makedirs(BUILD_DIR, exist_ok=True)
    compile_cmd = [
        "g++", "-std=c++17", "-O1", "-g",
        "-fsanitize=address,undefined", "-fno-omit-frame-pointer",
        "-I", os.path.join(PROJECT_DIR, "tools", "dspharness"),
        "-I", PROJECT_DIR,
        HARNESS_SRC,
        "-o", HARNESS_BIN,
        "-lpthread",
    ]
    try:
        result = subprocess.run(
            compile_cmd, capture_output=True, text=True, timeout=60,
        )
        compile_ok = result.returncode == 0
        compile_output = result.stderr + result.stdout
    except Exception as e:
        compile_ok = False
        compile_output = str(e)

    run_ok = False
    run_output = ""
    sections = []
    total_fails = 0

    if compile_ok:
        try:
            result = subprocess.run(
                [HARNESS_BIN], capture_output=True, text=True, timeout=120,
            )
            run_ok = result.returncode == 0
            run_output = result.stdout + result.stderr
            sections, total_fails = _parse_sections(run_output)
        except Exception as e:
            run_ok = False
            run_output = str(e)

    with _lock:
        _cache.update({
            "hash": _source_hash(),
            "compile_ok": compile_ok,
            "compile_output": compile_output,
            "run_ok": run_ok,
            "run_output": run_output,
            "sections": sections,
            "total_fails": total_fails,
        })


def _get_results():
    """Return cached results, rebuilding if source changed."""
    with _lock:
        cached_hash = _cache["hash"]
    current_hash = _source_hash()
    if cached_hash != current_hash:
        _run_harness()
    with _lock:
        return dict(_cache)


def _render_page(results):
    sections_html = ""
    for s in results["sections"]:
        status_class = "fail" if s["failed"] else "pass"
        checks_html = ""
        for c in s["checks"]:
            cls = "check-fail" if not c["ok"] else "check-ok"
            checks_html += f'<div class="{cls}">{html.escape(c["msg"])}</div>\n'
        sections_html += f"""
        <div class="section {status_class}">
          <div class="section-header">{html.escape(s["title"])} {html.escape(s["desc"])}</div>
          <div class="checks">{checks_html}</div>
        </div>"""

    if not results["compile_ok"]:
        banner = '<div class="banner banner-fail">COMPILATION FAILED</div>'
        detail = f'<pre class="output">{html.escape(results["compile_output"])}</pre>'
    elif not results["run_ok"]:
        banner = '<div class="banner banner-fail">HARNESS FAILED (non-zero exit)</div>'
        detail = f'<pre class="output">{html.escape(results["run_output"])}</pre>'
    else:
        banner = f'<div class="banner banner-pass">HARNESS PASS — {results["total_fails"]} failures</div>'
        detail = ""

    full_output = results["run_output"] or results["compile_output"]

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>NeonEQ — DSP Build Status</title>
<style>
  :root {{ --bg:#0a0a0f; --card:#15151f; --accent:#00e5ff; --pass:#00e676; --fail:#ff5252; --text:#e0e0e0; --dim:#888; }}
  * {{ margin:0; padding:0; box-sizing:border-box; }}
  body {{ background:var(--bg); color:var(--text); font-family:-apple-system,system-ui,sans-serif; padding:20px; line-height:1.6; }}
  h1 {{ color:var(--accent); font-size:1.5rem; margin-bottom:4px; }}
  .subtitle {{ color:var(--dim); font-size:0.9rem; margin-bottom:20px; }}
  .banner {{ padding:14px 18px; border-radius:10px; font-weight:700; font-size:1.1rem; margin-bottom:20px; text-align:center; }}
  .banner-pass {{ background:rgba(0,230,118,0.12); color:var(--pass); border:1px solid var(--pass); }}
  .banner-fail {{ background:rgba(255,82,82,0.12); color:var(--fail); border:1px solid var(--fail); }}
  .section {{ background:var(--card); border-radius:10px; padding:14px 16px; margin-bottom:10px; border-left:3px solid var(--pass); }}
  .section.fail {{ border-left-color:var(--fail); }}
  .section-header {{ font-weight:600; font-size:0.95rem; margin-bottom:8px; }}
  .checks {{ font-size:0.82rem; color:var(--dim); }}
  .check-ok::before {{ content:"✓ "; color:var(--pass); }}
  .check-fail::before {{ content:"✗ "; color:var(--fail); }}
  .check-fail {{ color:var(--fail); }}
  .output {{ background:#000; color:#ccc; padding:16px; border-radius:10px; font-size:0.78rem; overflow-x:auto; white-space:pre-wrap; max-height:400px; overflow-y:auto; margin-bottom:16px; }}
  .info {{ background:var(--card); border-radius:10px; padding:14px 16px; margin-bottom:16px; font-size:0.85rem; color:var(--dim); }}
  .info b {{ color:var(--text); }}
  details {{ margin-top:16px; }}
  summary {{ cursor:pointer; color:var(--accent); font-size:0.85rem; }}
</style>
</head>
<body>
  <h1>🎧 NeonEQ — SonicCore v146 (3.6.0)</h1>
  <div class="subtitle">Native DSP self-test harness — compiled &amp; run with g++ (ASan + UBSan), same as CI</div>
  {banner}
  <div class="info">
    <b>Project:</b> Android audio equalizer (Kotlin + C++ JNI)<br>
    <b>Build:</b> <code>g++ -std=c++17 -O1 -g -fsanitize=address,undefined</code><br>
    <b>Source:</b> <code>app/src/main/cpp/neondsp.cpp</code> + <code>tools/dspharness/harness.cpp</code><br>
    <b>Auto-rebuild:</b> Editing C++ source and reloading this page re-runs the harness.
  </div>
  {detail}
  {sections_html}
  <details>
    <summary>Full harness output</summary>
    <pre class="output">{html.escape(full_output)}</pre>
  </details>
</body>
</html>"""


class Handler(http.server.BaseHTTPRequestHandler):
    def _serve_page(self):
        results = _get_results()
        page = _render_page(results)
        body = page.encode()
        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if self.command == "GET":
                self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass  # client closed early (health-check proxy) — not an error

    def do_GET(self):
        self._serve_page()

    def do_HEAD(self):
        self._serve_page()

    def log_message(self, fmt, *args):
        pass  # suppress request logging


if __name__ == "__main__":
    # Build once at startup so the first request is fast.
    _run_harness()
    with _lock:
        r = _cache
        status = "PASS" if r["run_ok"] else "FAIL"
        print(f"Initial harness build: compile={'ok' if r['compile_ok'] else 'FAIL'} run={status} fails={r.get('total_fails', '?')}")
    server = http.server.HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Serving NeonEQ DSP build status on http://0.0.0.0:{PORT}")
    server.serve_forever()
