#!/usr/bin/env python3
"""Check, download, or import the exact JARs recorded in dependencies.lock.json (Python 3.11+)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import time
from urllib.error import URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def download(dependency, target):
    """Publish a download only after its full contents match the locked SHA-256."""
    expected = dependency["sha256"]
    if target.exists():
        if target.is_file() and digest(target) == expected:
            return
        raise ValueError("different file already exists; move it aside first")
    url = dependency["download_url"]
    if urlparse(url).scheme != "https":
        raise ValueError("download_url must use HTTPS")
    target.parent.mkdir(parents=True, exist_ok=True)
    request = Request(url, headers={"User-Agent": "krc-villagers-dependency-importer/1.0"})
    for attempt in range(3):
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=target.parent, prefix=target.name + ".", suffix=".part", delete=False) as out:
                temporary = Path(out.name)
                with urlopen(request, timeout=60) as response:
                    if urlparse(response.geturl()).scheme != "https":
                        raise ValueError("download redirected to a non-HTTPS URL")
                    shutil.copyfileobj(response, out, length=1024 * 1024)
            if digest(temporary) != expected:
                raise ValueError("download SHA-256 differs from the tested version")
            os.replace(temporary, target)
            return
        except (URLError, TimeoutError, ConnectionError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--from", dest="source", type=Path, help="Existing mods directory; reads only, does not change that modpack")
    mode.add_argument("--download", action="store_true", help="Download missing JARs from the public URLs in the lock file, verifying SHA-256")
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
        elif args.download:
            try:
                download(dependency, target)
                print("DOWNLOADED", dependency["path"], flush=True)
            except (OSError, ValueError, KeyError, URLError) as error:
                missing.append(f"{dependency['path']}: {error}")
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
