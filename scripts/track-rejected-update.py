#!/usr/bin/env python3
"""Report dependency candidates that failed the build, and track them in one GitHub issue (Python 3.11+).

--summarize   in the build job: write the run summary and the `rejected` output for the tracking job.
--track       in the tracking job: open one issue per rejected candidate set (never twice for the same set),
              and close issues that a newer candidate set or a published lock file has replaced.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MARKER = "<!-- krc-villagers:rejected-dependency-update "
ERROR = re.compile(r"\.java:\d+: error:|LogTestReporter.*\bfailed\b|required tests? failed|^> Task \S+ FAILED"
                   r"|GameTest did not confirm|InvalidInjectionException|MixinApplyError")
DETAIL = re.compile(r"^\s*(symbol|location):")


def describe(changes):
    return "、".join(f"{c['modid']} {c['before']} → {c['after']}" for c in changes)


def table(changes):
    rows = ["| 模組 | 目前 | 候選 |", "| --- | --- | --- |"]
    return rows + [f"| {c['modid']} | [{c['before']}]({c['before_source']}) | [{c['after']}]({c['after_source']}) |" for c in changes]


def candidate_key(changes):
    """Exact files, so a same-version re-upload is a different candidate."""
    return {c["modid"]: {"from": c["before_source"], "to": c["after_source"]} for c in changes}


def failure_lines(text, limit=40):
    """Compiler errors with their symbol, failed tasks and GameTests; Gradle repeats javac output, so dedupe."""
    lines = [re.sub(r"\x1b\[[0-9;]*m", "", line).rstrip() for line in text.splitlines()]
    found, seen = [], set()
    for index, line in enumerate(lines):
        if not ERROR.search(line):
            continue
        picked = [line] + [l for l in lines[index + 1:index + 5] if DETAIL.match(l)] if ".java:" in line else [line]
        for entry in picked:
            entry = re.sub(r"^\s*\S*?/(src/)", r"\1", entry) if ".java:" in entry else entry
            if entry.strip() not in seen:
                seen.add(entry.strip())
                found.append(entry)
    return found[:limit]


def summarize(root):
    changes = json.loads((root / "build/ci/dependency-updates.json").read_text(encoding="utf-8"))
    if not changes:
        raise ValueError("No dependency candidate was prepared; this is not a rejected update")
    log = root / "build/ci/gradle.log"
    errors = failure_lines(log.read_text(encoding="utf-8", errors="replace")) if log.exists() else []
    summary = ["### 依賴更新未通過驗證，未發布", "",
               "現有鎖定檔與已發布的 Release 保持不變；修正附屬模組後才會發布這批更新。", "", *table(changes), "",
               "編譯／GameTest 錯誤摘要（完整日誌在 `diagnostics` artifact 的 `ci/gradle.log`）：", "", "```text",
               *(errors or ["（日誌中沒有可辨識的錯誤行）"]), "```", ""]
    return changes, "\n".join(summary)


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True).strip()


def tracked_issues():
    issues = json.loads(gh("issue", "list", "--state", "open", "--limit", "200", "--json", "number,body"))
    found = []
    for issue in issues:
        body = issue.get("body") or ""
        start = body.find(MARKER)
        end = body.find(" -->", start)
        if start < 0 or end < 0:
            continue
        try:
            found.append((issue["number"], json.loads(body[start + len(MARKER):end])))
        except ValueError:
            continue
    return found


def track(changes, locked, run_url):
    """Keep at most one open issue, for the newest candidate set that has not been published yet.

    changes: the rejected candidates of this run, or [] after a successful publication.
    locked:  {modid: source_url} of the lock file this run tested.
    """
    key = candidate_key(changes) if changes else None
    current = None
    for number, candidates in tracked_issues():
        if key is not None and candidates == key:
            current = number
        elif key is not None:
            # Every check prepares all pending updates together, so a different set replaces the old one.
            gh("issue", "close", str(number), "--reason", "not planned",
               "--comment", f"已由較新的依賴候選取代，見 {run_url}")
            print(f"Closed superseded dependency tracking issue #{number}")
        elif all(locked.get(modid) != files["from"] for modid, files in candidates.items()):
            gh("issue", "close", str(number), "--reason", "completed",
               "--comment", f"已發布的鎖定檔不再使用這批舊檔案，且通過編譯與全部 GameTest：{run_url}")
            print(f"Closed resolved dependency tracking issue #{number}")
    if key is None:
        return None
    if current is not None:
        print(f"Dependency tracking issue #{current} is already open for {describe(changes)}")
        return current
    body = "\n".join([
        "依賴檢查找到新檔案，但編譯或伺服器 GameTest 未通過，因此**沒有發布**。現有鎖定檔與 Release 保持可用。", "",
        *table(changes), "",
        f"失敗的執行紀錄與錯誤摘要：{run_url}", "",
        "處理方式：在本機執行 `python3 scripts/update-dependencies.py` 取得同一批候選，修正與新版 API 的相容性，"
        "連同 `dependencies.lock.json`、`gradle.properties` 一起提交到 `main`。發布成功後此 issue 會自動關閉；"
        "候選不變時，之後的排程不會重複開 issue。", "",
        MARKER + json.dumps(key, ensure_ascii=False, sort_keys=True) + " -->",
    ])
    url = gh("issue", "create", "--title", f"依賴更新未通過驗證：{describe(changes)}", "--body", body)
    print(f"Opened dependency tracking issue {url}")
    return url


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--summarize", action="store_true")
    mode.add_argument("--track", action="store_true")
    args = parser.parse_args()
    if args.summarize:
        changes, summary = summarize(ROOT)
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as out:
            out.write(summary)
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
            out.write(f"rejected={json.dumps(changes, ensure_ascii=False, separators=(',', ':'))}\n")
        print(f"::warning title=Dependency update not published::{describe(changes)} failed the build or GameTests; "
              "the current lock file and release are kept.")
        return
    rejected = os.environ.get("REJECTED") or ""
    track(json.loads(rejected) if rejected else [], json.loads(os.environ["LOCKED"]), os.environ["RUN_URL"])


if __name__ == "__main__":
    main()
