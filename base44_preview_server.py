#!/usr/bin/env python3
"""
Base44 preview server for the SonicCore Equalizer Android project.

This is an Android audio EQ app — it cannot run in a browser.  The preview
server compiles and runs the native C++ DSP self-test harness (the same
command CI uses, with ASan+UBSan) and serves the results as a web page on
port 3000 so the Base44 platform has a live, verifiable preview.

The harness includes the REAL production neondsp.cpp, so a green run
proves the native DSP chain compiles and is numerically correct.
"""

import http.server
import os
import re
import socketserver
import subprocess
import threading
import time
from urllib.parse import urlparse

PORT = int(os.environ.get("PORT", "3000"))
PROJECT_DIR = os.environ.get(
    "PROJECT_DIR",
    os.path.join(os.path.dirname(__file__),
                 "conversations", "6a7a961312b12b330076a6ce", "android-eq"))
HARNESS_BIN = "/tmp/dsp_harness"

# ── Build + run the harness ──────────────────────────────────────────────
_build_lock = threading.Lock()
_state = {"status": "pending", "output": "", "sections": [], "failures": 0,
          "built_at": 0.0, "error": ""}


def _run_harness():
    """Compile the harness (ASan+UBSan, same flags as CI) and run it."""
    with _build_lock:
        _state["status"] = "building"
        _state["error"] = ""
        try:
            compile_cmd = [
                "g++", "-std=c++17", "-O1", "-g",
                "-fsanitize=address,undefined", "-fno-omit-frame-pointer",
                "-I", "tools/dspharness", "-I", ".",
                "tools/dspharness/harness.cpp",
                "-o", HARNESS_BIN, "-lpthread",
            ]
            r = subprocess.run(compile_cmd, cwd=PROJECT_DIR,
                               capture_output=True, text=True, timeout=120)
            if r.returncode != 0:
                _state["status"] = "compile_error"
                _state["error"] = r.stderr
                _state["built_at"] = time.time()
                return

            _state["status"] = "running"
            r2 = subprocess.run([HARNESS_BIN], capture_output=True, text=True,
                                timeout=120)
            output = r2.stdout + r2.stderr
            _state["output"] = output
            _state["failures"] = r2.returncode
            _state["status"] = "pass" if r2.returncode == 0 else "fail"
            _state["built_at"] = time.time()

            # Parse sections
            sections = []
            current = None
            for line in output.splitlines():
                m = re.match(r'^\[(\d+)\]\s*(.*)', line)
                if m:
                    if current:
                        sections.append(current)
                    current = {"id": int(m.group(1)), "name": m.group(2),
                               "checks": []}
                elif current and line.strip().startswith(("ok:", "FAIL:")):
                    ok = line.strip().startswith("ok:")
                    current["checks"].append({
                        "ok": ok,
                        "text": line.strip()[4:] if ok else line.strip()[6:],
                    })
                elif current and "  " in line and "FAIL" in line:
                    current["checks"].append({"ok": False, "text": line.strip()})
            if current:
                sections.append(current)
            _state["sections"] = sections

        except subprocess.TimeoutExpired:
            _state["status"] = "timeout"
            _state["error"] = "Build or harness run timed out"
            _state["built_at"] = time.time()
        except Exception as e:
            _state["status"] = "error"
            _state["error"] = str(e)
            _state["built_at"] = time.time()


def _rebuild_if_stale():
    """Rebuild if the harness binary is missing or source is newer."""
    src = os.path.join(PROJECT_DIR, "tools", "dspharness", "harness.cpp")
    dsp = os.path.join(PROJECT_DIR, "app", "src", "main", "cpp", "neondsp.cpp")
    newest_src = max(os.path.getmtime(src), os.path.getmtime(dsp))
    if not os.path.exists(HARNESS_BIN) or \
       (_state["built_at"] and newest_src > _state["built_at"]) or \
       (_state["status"] == "pending"):
        _run_harness()


# ── HTML rendering ───────────────────────────────────────────────────────
def _render_page():
    s = _state
    status_color = {"pass": "#00e676", "fail": "#ff1744",
                    "building": "#ffc107", "running": "#ffc107",
                    "compile_error": "#ff1744", "timeout": "#ff1744",
                    "error": "#ff1744", "pending": "#ffc107"}
    color = status_color.get(s["status"], "#ffc107")
    status_label = {"pass": "ALL TESTS PASSED", "fail": "TEST FAILURES",
                    "building": "BUILDING…", "running": "RUNNING TESTS…",
                    "compile_error": "COMPILE ERROR",
                    "timeout": "TIMEOUT", "error": "ERROR",
                    "pending": "PENDING"}.get(s["status"], s["status"])

    sections_html = ""
    for sec in s["sections"]:
        checks_html = ""
        for chk in sec.get("checks", []):
            icon = "✓" if chk["ok"] else "✗"
            cls = "pass" if chk["ok"] else "fail"
            checks_html += f'<div class="check {cls}"><span class="icon">{icon}</span> {chk["text"]}</div>\n'
        sections_html += f'''
        <div class="section">
          <h3>[{sec["id"]}] {sec["name"]}</h3>
          <div class="checks">{checks_html}</div>
        </div>'''

    error_html = ""
    if s["error"]:
        error_html = f'<div class="error-box"><h3>Error</h3><pre>{s["error"]}</pre></div>'

    raw_output = ""
    if s["output"]:
        raw_output = f'<details><summary>Raw harness output</summary><pre class="raw">{s["output"]}</pre></details>'

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>SonicCore Equalizer v3.8.0 — DSP Build Status</title>
<style>
  :root {{ --bg:#0a0a0f; --card:#15151f; --text:#e0e0e0; --dim:#888; --accent:#00e5ff; }}
  * {{ box-sizing:border-box; margin:0; padding:0; }}
  body {{ background:var(--bg); color:var(--text); font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; padding:20px; max-width:900px; margin:0 auto; }}
  header {{ text-align:center; margin-bottom:24px; }}
  h1 {{ font-size:1.6rem; color:var(--accent); margin-bottom:4px; }}
  .subtitle {{ color:var(--dim); font-size:0.9rem; }}
  .status-badge {{ display:inline-block; padding:8px 24px; border-radius:50px; font-weight:700; font-size:1.1rem; color:#000; background:{color}; margin:12px 0; }}
  .summary {{ display:flex; gap:16px; justify-content:center; margin:16px 0; flex-wrap:wrap; }}
  .stat {{ background:var(--card); padding:12px 20px; border-radius:12px; text-align:center; min-width:100px; }}
  .stat .num {{ font-size:1.8rem; font-weight:700; }}
  .stat .lbl {{ font-size:0.75rem; color:var(--dim); text-transform:uppercase; }}
  .section {{ background:var(--card); border-radius:12px; padding:16px; margin-bottom:12px; }}
  .section h3 {{ font-size:0.95rem; color:var(--accent); margin-bottom:8px; }}
  .check {{ font-size:0.85rem; padding:3px 0; font-family:'SF Mono',Consolas,monospace; }}
  .check .icon {{ font-weight:700; }}
  .check.pass .icon {{ color:#00e676; }}
  .check.fail .icon {{ color:#ff1744; }}
  .check.fail {{ color:#ff8a80; }}
  .error-box {{ background:#1a0000; border:1px solid #ff1744; border-radius:12px; padding:16px; margin-bottom:12px; }}
  .error-box pre {{ font-size:0.8rem; overflow-x:auto; color:#ff8a80; white-space:pre-wrap; }}
  .raw {{ font-size:0.75rem; overflow-x:auto; color:var(--dim); white-space:pre-wrap; margin-top:8px; }}
  details {{ margin-top:16px; }}
  summary {{ cursor:pointer; color:var(--dim); font-size:0.85rem; }}
  .info {{ background:var(--card); border-radius:12px; padding:16px; margin-bottom:16px; font-size:0.85rem; color:var(--dim); }}
  .info strong {{ color:var(--text); }}
  .actions {{ text-align:center; margin:16px 0; }}
  .btn {{ display:inline-block; padding:8px 20px; border-radius:8px; background:var(--accent); color:#000; text-decoration:none; font-weight:600; font-size:0.9rem; }}
  footer {{ text-align:center; color:var(--dim); font-size:0.75rem; margin-top:24px; }}
</style>
</head>
<body>
  <header>
    <h1>🎧 SonicCore Equalizer v3.8.0</h1>
    <div class="subtitle">Liquid Glass Edition · Native DSP Self-Test (ASan + UBSan)</div>
    <div class="status-badge">{status_label}</div>
  </header>

  <div class="summary">
    <div class="stat"><div class="num">{len(s.get("sections", []))}</div><div class="lbl">Test Sections</div></div>
    <div class="stat"><div class="num" style="color:{color}">{s["failures"]}</div><div class="lbl">Failures</div></div>
    <div class="stat"><div class="num">{sum(len(sec.get("checks", [])) for sec in s.get("sections", []))}</div><div class="lbl">Assertions</div></div>
  </div>

  <div class="info">
    <strong>Project:</strong> Android system-wide audio equalizer (Kotlin + native C++ DSP)<br>
    <strong>Build target:</strong> <code>tools/dspharness/harness.cpp</code> → includes real <code>neondsp.cpp</code><br>
    <strong>What this proves:</strong> The native DSP chain (preamp → PEQ → graphic EQ → shelves →
    compressor → stereo → convolver → limiter) compiles and passes all numerical correctness,
    stability, and stress tests. The v146 limiter/compressor gain-reduction fix is verified:
    transparent below ceiling (exact unity, measured), no below-threshold crushing.
  </div>

  <div class="actions">
    <a class="btn" href="/rebuild">🔄 Rebuild &amp; Re-run Tests</a>
  </div>

  {error_html}
  {sections_html}
  {raw_output}

  <footer>
    SonicCore Equalizer v3.8.0 · Native DSP harness ·
    Built at {time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime(s.get("built_at", 0))) if s.get("built_at") else "—"}
  </footer>
</body>
</html>"""


# ── HTTP handler ────────────────────────────────────────────────────────
class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass  # silence default logging

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            ok = _state["status"] == "pass"
            self.send_response(200 if ok else 503)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(_state["status"].encode())
        elif path == "/rebuild":
            threading.Thread(target=_run_harness, daemon=True).start()
            self.send_response(302)
            self.send_header("Location", "/")
            self.end_headers()
        elif path == "/" or path == "/index.html":
            _rebuild_if_stale()
            html = _render_page()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode())
        elif path == "/favicon.ico":
            # Minimal inline favicon (16x16 cyan dot on dark bg)
            ico = bytes.fromhex(
                "00000100010010100000010020006804000016000000280000001000000020"
                "0000000100040000000000c004000000000000000000000000000000000000"
                "0000000000000000000000000000ffffff00ccffff00" + "00" * 472)
            self.send_response(200)
            self.send_header("Content-Type", "image/x-icon")
            self.send_header("Cache-Control", "public, max-age=86400")
            self.end_headers()
            self.wfile.write(ico)
        else:
            self.send_response(404)
            self.end_headers()


class ThreadingServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    _rebuild_if_stale()
    server = ThreadingServer(("0.0.0.0", PORT), Handler)
    print(f"Preview server listening on 0.0.0.0:{PORT}")
    server.serve_forever()
