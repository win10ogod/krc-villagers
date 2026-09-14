#!/usr/bin/env python3
"""Publish the verified artifacts of this Actions run, without rebuilding or replacing a published release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from urllib.parse import quote


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True).strip()


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def prepare_assets(root, version):
    build = root / "build"
    expected = {f"libs/krc-villagers-{version}.jar", f"libs/krc-villagers-{version}-sources.jar",
                f"distributions/krc-villagers-{version}-project.zip"}
    recorded = dict((name, sha) for sha, name in (line.split(maxsplit=1) for line in (build / "SHA256SUMS.txt").read_text().splitlines()))
    if set(recorded) != expected:
        raise ValueError("Build manifest does not contain exactly the expected release artifacts")
    for name, sha in recorded.items():
        if digest(build / name) != sha:
            raise ValueError(f"Build artifact SHA-256 mismatch: {name}")
    out = build / "release-assets"
    out.mkdir(exist_ok=True)
    for name in sorted(expected):
        shutil.copyfile(build / name, out / Path(name).name)
    checksums = "".join(f"{recorded[name]}  {Path(name).name}\n" for name in sorted(expected))
    (out / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8")
    return [out / Path(name).name for name in sorted(expected)] + [out / "SHA256SUMS.txt"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-only', action='store_true', help='Verify and stage build assets without publishing')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    version = re.search(r"^mod_version=(.+)$", (root / "gradle.properties").read_text(), re.MULTILINE)[1]
    assets = prepare_assets(root, version)
    if args.verify_only:
        print('Verified all release artifacts against the build manifest')
        return
    repo = os.environ["GITHUB_REPOSITORY"]
    sha = os.environ["RELEASE_SHA"]
    ref = os.environ["GITHUB_REF"]
    updated = os.environ.get("DEPENDENCIES_UPDATED") == "true"
    stable = updated or ref.startswith("refs/tags/v")
    tag = "v" + version if stable else "dev-" + os.environ["GITHUB_RUN_ID"]
    if ref.startswith("refs/tags/v") and ref != "refs/tags/" + tag:
        raise ValueError("Version tag differs from gradle.properties")
    lock = json.loads((root / "dependencies.lock.json").read_text())
    base = f"https://github.com/{repo}"
    run = f"{base}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    notes = [f"假面騎士村民 **{version}**" + ("" if stable else f" 開發預覽版（{sha[:7]}）"), "",
             f"適用 Minecraft **{lock['minecraft']}**、NeoForge **{lock['neoforge']}**、Java **{lock['java']}**。",
             f"下載 `krc-villagers-{version}.jar` 放入遊戲的 `mods` 資料夾；多人遊戲的伺服器與玩家皆需安裝。",
             "", "招募原版村民、交付 KRC 腰帶變身、自動鎖定敵人，使用騎士拳／踢及形態技能。**Shift＋右鍵**管理裝備與行為，普通右鍵保留交易。",
             "", f"成品來自[本次自動編譯與測試]({run})，來源提交：[`{sha}`]({base}/commit/{sha})。",
             "伺服器 GameTest 必須全部通過才會發布；此次自動流程不執行圖形客戶端測試。", ""]
    report = root / "build/ci/dependency-updates.json"
    changes = json.loads(report.read_text()) if report.exists() else []
    if changes:
        notes += ["### 依賴更新", ""]
        notes += [f"- {e['modid']}：[{e['before']}]({e['before_source']}) → [{e['after']}]({e['after_source']})" for e in changes]
        notes += [""]
    notes += ["### 此版本使用的依賴", "", "依賴需另外安裝，未包入本模組 JAR。同版本號的重傳檔案可能不同，請使用下列檔案連結。", "",
              "| 模組 | 版本 | 下載頁 |", "| --- | --- | --- |"]
    notes += [f"| {e['modid']} | {e['version']} | [檔案]({e['source_url']}) |" for e in lock["files"]]
    notes += ["", f"開發用 `-sources.jar`、完整專案 ZIP 與 `SHA256SUMS.txt` 一併提供。校驗檔使用 Release 附件的檔名，可在下載目錄執行 `sha256sum -c SHA256SUMS.txt`。",
              "", f"[操作與開發說明]({base}/blob/{sha}/README.md) · [測試範圍]({base}/blob/{sha}/docs/verification.md)", ""]
    notes_file = root / "build/ci/release-notes.md"
    notes_file.parent.mkdir(parents=True, exist_ok=True)
    notes_file.write_text("\n".join(notes), encoding="utf-8")
    endpoint = f"repos/{repo}/releases/tags/{quote(tag, safe='')}"
    found = subprocess.run(["gh", "api", endpoint], capture_output=True, text=True)
    existing = None
    if found.returncode == 0:
        existing = json.loads(found.stdout)
        if existing["target_commitish"] != sha:
            raise ValueError("Existing release targets a different commit; use a new version")
    elif "HTTP 404" not in found.stderr:
        raise RuntimeError(found.stderr)
    if existing and not existing["draft"]:
        remote = {a["name"]: a.get("digest") for a in existing["assets"]}
        expected = {p.name: "sha256:" + digest(p) for p in assets}
        if remote != expected:
            raise ValueError("Published release assets differ; published releases are not overwritten")
        url = existing["html_url"]
    else:
        if existing is None:
            args = ["release", "create", tag, "--repo", repo, "--draft", "--target", sha,
                    "--title", f"KRC Villagers {tag}", "--notes-file", str(notes_file)]
            if not stable or "-" in version:
                args += ["--prerelease", "--latest=false"]
            if ref.startswith("refs/tags/v"):
                args += ["--verify-tag"]
            gh(*args)
        gh("release", "upload", tag, *map(str, assets), "--repo", repo, "--clobber")
        gh("release", "edit", tag, "--repo", repo, "--notes-file", str(notes_file), "--draft=false")
        published = json.loads(gh("api", endpoint))
        if published["draft"] or {a["name"]: a.get("digest") for a in published["assets"]} != {p.name: "sha256:" + digest(p) for p in assets}:
            raise ValueError("Release did not publish all expected assets with matching SHA-256")
        url = published["html_url"]
    print(url)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"\nPublished release: [{tag}]({url})\n")


if __name__ == "__main__":
    main()
