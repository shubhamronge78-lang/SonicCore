#!/usr/bin/env python3
"""Verify that every relative link in this project's Markdown resolves.

Usage (from the android-eq project directory):
    python3 tools/check_docs_links.py [path ...]

Without arguments it scans every *.md file under the directory that contains
this script's parent (the project root). Absolute URLs, mailto: links and
in-page anchors are skipped — they cannot be checked offline. Exit status is 0
when every relative link resolves and 1 otherwise.
"""

from __future__ import annotations

import os
import re
import sys

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
SKIP_PREFIXES = ("http://", "https://", "mailto:", "#", "tel:")


def markdown_files(paths: list[str]) -> list[str]:
    found: list[str] = []
    for path in paths:
        if os.path.isdir(path):
            for root, _dirs, files in os.walk(path):
                found.extend(
                    os.path.join(root, name) for name in sorted(files) if name.endswith(".md")
                )
        elif path.endswith(".md"):
            found.append(path)
    return sorted(set(found))


def check_file(path: str) -> list[str]:
    base = os.path.dirname(os.path.abspath(path))
    problems: list[str] = []
    try:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
    except OSError as error:  # pragma: no cover - filesystem error
        return [f"{path}: cannot read ({error})"]

    for target in LINK.findall(text):
        if target.startswith(SKIP_PREFIXES):
            continue
        relative = target.split("#", 1)[0]
        if not relative:
            continue
        resolved = os.path.normpath(os.path.join(base, relative))
        if not os.path.exists(resolved):
            problems.append(f"{path}: broken link -> {target}")
    return problems


def main(argv: list[str]) -> int:
    project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    paths = argv or [project_root]
    files = markdown_files(paths)
    if not files:
        print("no markdown files found")
        return 1

    problems: list[str] = []
    for path in files:
        problems.extend(check_file(path))

    for path in files:
        print(f"checked {path}")
    if problems:
        print()
        for problem in problems:
            print(problem)
        print(f"\nFAIL: {len(problems)} broken link(s) in {len(files)} file(s)")
        return 1
    print(f"\nPASS: every relative link resolves in {len(files)} file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
