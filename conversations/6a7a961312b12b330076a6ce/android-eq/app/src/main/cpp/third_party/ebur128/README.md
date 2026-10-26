# libebur128 (vendored)

Provenance of the two files in this directory. Everything here is unmodified
upstream source, so the audit trail is this file plus the checksum below.

| | |
| --- | --- |
| **Upstream** | <https://github.com/jiixyj/libebur128> (canonical development is on Xiph's GitLab; this mirror is what the inventory audited) |
| **Release** | **v1.2.6** (2021-02-14), the newest release |
| **Pinned commit** | `67b33abe1558160ed76ada1322329b0e9e058b02` (`master`, fetched 2026-10-26) |
| **Licence** | **MIT** — © 2011 Jan Kokemüller, verbatim in [`LICENSE`](LICENSE) |
| **Taken** | `ebur128.c` (53 176 bytes, sha256 `c2fc562f1088cacab4d21250b6e04996ef36c7694ea901e08cc4d64eb542b78d`) and `ebur128.h` (15 547 bytes). Both byte-identical to the pinned commit. |
| **Not taken** | The project's CMake build (SonicCore compiles the single `.c` file directly), its `queue/` copy of `sys/queue.h` (the Android NDK sysroot ships one), its test suite, its `loudness-scanner` companion tool, and any measurement data. |
| **Why this version** | ≥ v1.2.3 fixed an uninitialised-memory read in true-peak scanning and ≥ v1.2.5 added channel/sample-rate limits against integer overflow. |
| **How it is built** | `app/src/main/cpp/CMakeLists.txt` compiles it into `libloudness.so` — a separate shared library from `libneondsp.so`, so the measurement code is not part of the playback DSP chain. |
| **Attribution** | MIT requires the copyright notice and permission notice to travel with the source, which is why `LICENSE` is in this directory. |

Recorded in `docs/repository-integration.md` (§1.2) and `docs/repository-inventory.md`.

## Re-vendoring

```bash
SHA=<commit>            # e.g. the next release tag's commit
curl -sSL "https://codeload.github.com/jiixyj/libebur128/tar.gz/$SHA" -o src.tar.gz
tar xzf src.tar.gz
cp libebur128-$SHA/ebur128/ebur128.{c,h} app/src/main/cpp/third_party/ebur128/
cp libebur128-$SHA/COPYING             app/src/main/cpp/third_party/ebur128/LICENSE
sha256sum app/src/main/cpp/third_party/ebur128/ebur128.c   # update the checksum above
```

Then re-run the loudness harness in the container (`build-output/loudness-test.log`)
— it pins the numbers the measurement must produce, so a re-vendor that changes
them fails loudly instead of silently shifting what the app reports.
