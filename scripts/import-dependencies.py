#!/usr/bin/env python3
"""Check local dependencies, or copy the exact tested JARs from an existing mods folder."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from", dest="source", type=Path, help="Existing mods directory; reads only, does not change that modpack")
    args = parser.parse_args()
    lock = json.loads((ROOT / "dependencies.lock.json").read_text(encoding="utf-8"))
    candidates = {}
    if args.source:
        if not args.source.is_dir():
            parser.error("--from must name an existing directory")
        for path in args.source.glob("*.jar"):
            candidates[digest(path)] = path
    missing = []
    for dependency in lock["files"]:
        target = ROOT / dependency["path"]
        expected = dependency["sha256"]
        if target.is_file() and digest(target) == expected:
            print("OK", dependency["path"])
        elif expected in candidates:
            if target.exists():
                missing.append(f"{dependency['path']}: different file already exists; move it aside first")
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(candidates[expected], target)
            print("COPIED", dependency["path"])
        else:
            missing.append(f"{dependency['path']}: missing or SHA-256 differs from the tested version")
    for message in missing:
        print("ERROR", message, file=sys.stderr)
    return bool(missing)


if __name__ == "__main__":
    sys.exit(main())
