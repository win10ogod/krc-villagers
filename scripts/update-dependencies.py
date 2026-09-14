#!/usr/bin/env python3
"""Prepare newer Minecraft/NeoForge dependencies locally; never commit or publish them."""
import argparse
import copy
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import tempfile
import time
import tomllib
from urllib.error import URLError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
CHANNELS = {"release": {"release"}, "beta": {"release", "beta"}, "alpha": {"release", "beta", "alpha"}}


def fetch(url, headers=None):
    request = Request(url, headers={"User-Agent": "krc-villagers-updater/1.0 (https://github.com/win10ogod/krc-villagers)", **(headers or {})})
    for attempt in range(3):
        try:
            with urlopen(request, timeout=60) as response:
                return response.read()
        except (URLError, TimeoutError, ConnectionError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def get_json(url, headers=None):
    return json.loads(fetch(url, headers))


def select_curseforge(files, current, minecraft):
    eligible = [f for f in files if minecraft in f["versions"]
                and "NeoForge" in f["versions"] and f["type"] in CHANNELS[current["channel"]]]
    if not eligible:
        raise ValueError("CurseForge index contains no matching Minecraft/NeoForge files")
    latest = max(eligible, key=lambda f: int(f["id"]))
    if int(latest["id"]) <= current["file_id"]:
        if int(latest["id"]) < current["file_id"]:
            warning = f"CurseForge index stops at {latest['id']}, older than pinned {current['file_id']}; keeping the newer pinned file"
            print("WARNING:", warning, flush=True)
            if os.environ.get("GITHUB_STEP_SUMMARY"):
                with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
                    summary.write(f"\nDependency index warning: {warning}.\n")
        return None
    return latest


def select_modrinth(versions, current, minecraft):
    eligible = [v for v in versions if minecraft in v["game_versions"]
                and "neoforge" in v["loaders"] and v["version_type"] in CHANNELS[current["channel"]]]
    if not eligible:
        raise ValueError("Modrinth contains no matching Minecraft/NeoForge versions")
    latest = max(eligible, key=lambda v: datetime.fromisoformat(v["date_published"].replace("Z", "+00:00")))
    if latest["id"] == current["version_id"] or datetime.fromisoformat(latest["date_published"].replace("Z", "+00:00")) <= datetime.fromisoformat(current["published_at"].replace("Z", "+00:00")):
        return None
    return latest


def candidate(entry, minecraft):
    current = entry["update"]
    project = current["project_id"]
    if current["provider"] == "curseforge":
        key = os.environ.get("CURSEFORGE_API_KEY")
        if key:
            # The API is paginated; inspect all matching files, not an arbitrary first page.
            files, index = [], 0
            while True:
                query = urlencode({"gameVersion": minecraft, "modLoaderType": 6, "pageSize": 50, "index": index})
                response = get_json(f"https://api.curseforge.com/v1/mods/{project}/files?{query}", {"x-api-key": key})
                for item in response["data"]:
                    files.append({"id": item["id"], "name": item["fileName"], "versions": item["gameVersions"],
                                  "type": {1: "release", 2: "beta", 3: "alpha"}[item["releaseType"]],
                                  "hashes": {"sha1": h["value"] for h in item["hashes"] if h["algo"] == 1}})
                index += response["pagination"]["resultCount"]
                if index >= response["pagination"]["totalCount"]:
                    break
                if response["pagination"]["resultCount"] == 0:
                    raise ValueError("CurseForge pagination stopped before all files were read")
        else:
            print(f"Checking CurseForge project {project} through the public CFWidget index", flush=True)
            files = get_json(f"https://api.cfwidget.com/{project}")["files"]
        latest = select_curseforge(files, current, minecraft)
        if latest is None:
            return None
        file_id = int(latest["id"])
        filename = latest["name"]
        url = f"https://edge.forgecdn.net/files/{file_id // 1000}/{file_id % 1000}/{quote(filename, safe='')}"
        source = f"{entry['project']}/files/{file_id}"
        update = {**current, "file_id": file_id}
        # Preserve GH's file-ID naming even when the upstream version number is reused.
        local = f"generic_henshin-cf{file_id}.jar" if entry["modid"] == "generic_henshin" else filename
        return url, source, local, update, latest.get("hashes", {})
    if current["provider"] != "modrinth":
        raise ValueError(f"Unknown update provider: {current['provider']}")
    query = urlencode({"game_versions": json.dumps([minecraft]), "loaders": '["neoforge"]'})
    versions = get_json(f"https://api.modrinth.com/v2/project/{project}/version?{query}")
    latest = select_modrinth(versions, current, minecraft)
    if latest is None:
        return None
    jars = [f for f in latest["files"] if f["filename"].endswith(".jar")]
    primary = [f for f in jars if f["primary"]]
    if len(primary or jars) != 1:
        raise ValueError(f"Ambiguous Modrinth JARs for {entry['modid']}")
    file = (primary or jars)[0]
    update = {**current, "version_id": latest["id"], "published_at": latest["date_published"]}
    return file["url"], f"https://modrinth.com/mod/{project}/version/{latest['id']}", file["filename"], update, file["hashes"]


def jar_metadata(path, modid):
    with ZipFile(path) as jar:
        metadata = next((p for p in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml") if p in jar.namelist()), None)
        if metadata is None:
            raise ValueError(f"No Forge/NeoForge metadata in {path.name}")
        data = tomllib.loads(jar.read(metadata).decode("utf-8"))
        mod = next((m for m in data["mods"] if m["modId"] == modid), None)
        if mod is None:
            raise ValueError(f"Downloaded JAR does not contain mod ID {modid}")
        version = mod["version"]
        if version == "${file.jarVersion}":
            manifest = jar.read("META-INF/MANIFEST.MF").decode().replace("\r\n", "\n").replace("\n ", "")
            version = re.search(r"^Implementation-Version: (.+)$", manifest, re.MULTILINE).group(1)
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.+_\-]*", version):
            raise ValueError(f"Unsupported mod version {version!r}")
        return version, data.get("license", "")


def bump_version(text):
    match = re.search(r"^mod_version=(\d+)\.(\d+)\.(\d+)$", text, re.MULTILINE)
    if match is None:
        raise ValueError("Automatic dependency releases require mod_version=X.Y.Z")
    version = f"{match[1]}.{match[2]}.{int(match[3]) + 1}"
    return text[:match.start()] + "mod_version=" + version + text[match.end():]


def update(root):
    lock_path = root / "dependencies.lock.json"
    lock = json.loads(lock_path.read_text(encoding="utf-8"))
    revised = copy.deepcopy(lock)
    changes = []
    (root / ".work").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=root / ".work", prefix="dependency-update-") as temp:
        staged = []
        for entry in revised["files"]:
            found = candidate(entry, lock["minecraft"])
            if found is None:
                print("CURRENT", entry["modid"], entry["version"], flush=True)
                continue
            url, source, filename, tracking, hashes = found
            if Path(filename).name != filename or not filename.endswith(".jar"):
                raise ValueError("Invalid upstream filename")
            data = fetch(url)
            for algorithm, expected in hashes.items():
                if algorithm in {"sha1", "sha512", "sha256"} and hashlib.new(algorithm, data).hexdigest() != expected:
                    raise ValueError(f"Upstream hash mismatch for {entry['modid']}")
            path = Path(temp) / filename
            path.write_bytes(data)
            version, license_name = jar_metadata(path, entry["modid"])
            target = str(Path(entry["path"]).parent / filename)
            sha256 = hashlib.sha256(data).hexdigest()
            changes.append({"modid": entry["modid"], "before": entry["version"], "after": version,
                            "before_source": entry["source_url"], "after_source": source})
            entry.update(path=target, version=version, license=license_name, sha256=sha256,
                         source_url=source, download_url=url, update=tracking)
            staged.append((path, root / target, sha256))
            print("UPDATE", changes[-1], flush=True)
        if changes:
            properties = root / "gradle.properties"
            new_properties = bump_version(properties.read_text(encoding="utf-8"))
            for source, target, sha256 in staged:
                if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() != sha256:
                    raise ValueError(f"Different local file exists at {target}; move it aside first")
            for source, target, _ in staged:
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
            lock_path.write_text(json.dumps(revised, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            properties.write_text(new_properties, encoding="utf-8")
    return changes


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT, help="Candidate checkout to update")
    args = parser.parse_args()
    changes = update(args.root.resolve())
    report = args.root / "build/ci/dependency-updates.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(changes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
            out.write(f"changed={str(bool(changes)).lower()}\n")
    print(f"Dependency check complete: {len(changes)} update(s)")


if __name__ == "__main__":
    main()
