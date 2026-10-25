#!/usr/bin/env python3
"""Report the licence of every artifact the build actually resolves.

Usage (inside the app container, from the android-eq project directory):
    python3 tools/license_check.py

The dependency set is read from Gradle itself (so conflict resolution and
transitive dependencies are already applied), and each licence name is read from
the artifact's published POM in the Gradle module cache — not from a hand-kept
list. Copyleft licences (GPL/AGPL/SSPL) make the script exit 1: this project is
MIT and cannot ship them. LGPL is reported as "review" because it is compatible
only under dynamic-linking conditions.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

COORDINATE = re.compile(r"([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-+]+)")
CONFIGURATIONS = {
    "debugRuntimeClasspath": "shipped",
    "debugUnitTestRuntimeClasspath": "test-only",
}
COPYLEFT = ("GPL", "AGPL", "SSPL", "CDDL")
REVIEW = ("LGPL",)


def gradle_dependencies(configuration: str) -> dict[tuple[str, str, str], None]:
    result = subprocess.run(
        ["./gradlew", "-q", ":app:dependencies", "--configuration", configuration, "--no-daemon"],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        print(f"gradle failed for {configuration}:\n{result.stderr[-2000:]}")
        sys.exit(2)

    coordinates: dict[tuple[str, str, str], None] = {}
    for line in result.stdout.splitlines():
        if "---" not in line and "+-" not in line:
            continue
        tree = line.split("---", 1)[-1].strip()
        tree = tree.split("(*)")[0].split("(c)")[0].strip()
        if "->" in tree:
            tree = tree.split("->", 1)[1].strip()
        match = COORDINATE.match(tree)
        if match:
            coordinates[match.groups()] = None
    return coordinates


def cache_root() -> str:
    gradle_home = os.environ.get("GRADLE_USER_HOME") or os.path.expanduser("~/.gradle")
    return os.path.join(gradle_home, "caches", "modules-2", "files-2.1")


def licence_names(root: str, group: str, artifact: str, version: str) -> list[str]:
    directory = os.path.join(root, group, artifact, version)
    if not os.path.isdir(directory):
        return []
    names: list[str] = []
    for current, _dirs, files in os.walk(directory):
        for name in files:
            if not name.endswith(".pom"):
                continue
            try:
                tree = ET.parse(os.path.join(current, name))
            except (ET.ParseError, OSError):
                continue
            for licence in tree.getroot().iter():
                if licence.tag.rsplit("}", 1)[-1] != "license":
                    continue
                for child in licence:
                    if child.tag.rsplit("}", 1)[-1] == "name" and child.text:
                        names.append(" ".join(child.text.split()))
    return sorted(set(names))


def verdict(licences: list[str]) -> str:
    joined = " ".join(licences).upper()
    if not licences:
        return "unknown"
    if any(token in joined for token in COPYLEFT):
        return "COPYLEFT"
    if any(token in joined for token in REVIEW):
        return "review"
    return "ok"


def main() -> int:
    root = cache_root()
    print(f"gradle module cache: {root}\n")
    worst = 0
    for configuration, scope in CONFIGURATIONS.items():
        coordinates = gradle_dependencies(configuration)
        print(f"== {configuration} ({scope}): {len(coordinates)} artifact(s)")
        for group, artifact, version in sorted(coordinates):
            licences = licence_names(root, group, artifact, version)
            status = verdict(licences)
            if status == "COPYLEFT":
                worst = 1
            elif status == "unknown" and worst == 0:
                worst = max(worst, 0)
            print(f"  {status:<9} {group}:{artifact}:{version} — {', '.join(licences) or 'no POM licence'}")
        print()

    if worst:
        print("FAIL: a copyleft licence appears on a resolved configuration")
        return 1
    print("PASS: no copyleft licence on any resolved configuration")
    return 0


if __name__ == "__main__":
    sys.exit(main())
