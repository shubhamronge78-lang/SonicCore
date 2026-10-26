# Telling one SonicCore APK from another

The redesign existed in the source while the phone kept showing the old UI. That
is not a rendering bug: it is what happens when the APK you install is not the
APK you think you installed. Three things have to agree before the phone shows a
new build, and each of them used to be unverifiable.

## 1. Android must accept the install at all

Android replaces an installed app only when **both** of these hold:

| what | requirement | where this repo stands |
| --- | --- | --- |
| `applicationId` | identical to the installed app | `com.soniccore.app` |
| `versionCode` | **≥** the installed one | `1_000_000 + GITHUB_RUN_NUMBER` (so `1_000_000` locally) |
| signing certificate | identical to the installed app | `app/debug.keystore.p12`, checked in |

A lower `versionCode` or a different certificate makes the installer refuse the
update — and in practice the phone just keeps the app it already had, which looks
exactly like "the new APK is still the old UI".

Both of those causes were live before this pass:

- `versionCode` was `GITHUB_RUN_NUMBER`, or **1** for any build that did not run
  on CI (the sandbox, a local build). Every published CI APK therefore had a
  higher code than any local build, so a local APK could never replace it.
- The debug keystore was AGP's auto-generated one, which is **different on every
  CI runner**, so a new CI build could not replace the previous one either.

The base of `1_000_000` is deliberately far above the run-numbered builds that
were already published, so the first install of this scheme always replaces them.

## 2. The artifact must be the build you asked for

Both workflows used to upload a file called `app-debug-apk` from every branch, so
two downloads were indistinguishable. Now:

- the artifact name carries the ref and the commit
  (`app-debug-apk-<ref>-<sha>`),
- `BUILD-INFO.txt` travels inside the artifact with `ref`, `commit`,
  `workflow_run`, `size_bytes` and `sha256`,
- the run summary prints the same identity table.

The sandbox build writes the same identity to `build-output/apk-info.txt`, which
the status page on port 3000 displays next to the download button:

```
artifact=NeonEQ-debug.apk
variant=debug
source_branch=<branch>
source_commit=<sha>
size_bytes=<n>
sha256=<sha256>
badging:package: name="com.soniccore.app" versionCode="1000000" versionName="3.6.0" …
```

`source_branch` / `source_commit` come from `SONICCORE_BRANCH` / `SONICCORE_COMMIT`
when the stack is started with them set:

```bash
SONICCORE_BRANCH=$(git rev-parse --abbrev-ref HEAD) \
SONICCORE_COMMIT=$(git rev-parse HEAD) \
docker compose -f docker-compose.base44.yml up -d
```

## 3. The installed app must be the file you downloaded

On the phone:

```bash
# the versionCode the phone is running — compare it with the APK's identity
adb shell dumpsys package com.soniccore.app | grep versionCode

# the SHA-256 of the file you downloaded
sha256sum app-debug.apk
```

If `versionCode` is lower than the APK's, the install did not replace the app.
**Uninstalling first clears the app's data** (presets, saved DSP chains, session
history, the operation log) — back it up from Settings → Diagnostics → export
before doing that, and only uninstall when you are willing to lose it.

```bash
# install over the top, keeping data
adb install -r app-debug.apk

# if, and only if, the certificate differs (INSTALL_FAILED_UPDATE_INCOMPATIBLE):
#   this ERASES the app's data
adb uninstall com.soniccore.app && adb install app-debug.apk
```

## 4. What "the redesign is on screen" means here

There is no emulator in this environment (`/dev/kvm` is absent), so a device
install cannot be automated from here. The evidence that the shipped screens
draw the redesign is produced by the render tests instead:

- `ShippedScreenSnapshotTest` drives the real shell (`EqualizerScreen`) through
  its own navigation state and captures what each destination paints to
  `app/build/ui-screenshots/shipped-*.png`; the sandbox publishes them at
  `/screens.html` on port 3000.
- `ChainEditorInteractionTest` presses the stage switches, the reorder buttons
  and the Audio Console's inline switch, and asserts the chain that leaves the
  screen.

Neither of them proves anything about a phone that is running a different APK —
that is what sections 1–3 are for.
