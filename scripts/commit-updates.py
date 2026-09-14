#!/usr/bin/env python3
"""Commit tested dependency metadata, or reuse the identical commit after a publish retry."""
import os
from pathlib import Path
import subprocess


def commit_updates(root, base_sha):
    def git(*args):
        return subprocess.check_output(["git", *args], cwd=root, text=True).strip()

    changed = set(git("diff", "--name-only").splitlines())
    if not changed or not changed <= {"dependencies.lock.json", "gradle.properties"}:
        raise ValueError("Only tested dependency lock and version changes may be committed")
    remote = git("ls-remote", "origin", "refs/heads/main").split()[0]
    if remote != base_sha:
        git("fetch", "--no-tags", "origin", "main")
        identical = subprocess.run(["git", "diff", "--quiet", remote, "--", "."], cwd=root).returncode == 0
        if git("rev-parse", remote + "^") == base_sha and identical:
            print("Reusing the identical tested dependency commit from the previous publish attempt")
            return remote
        raise ValueError("main changed during testing; the next dependency check must retry on the new source")
    git("config", "user.name", "github-actions[bot]")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    git("add", "dependencies.lock.json", "gradle.properties")
    git("commit", "-m", "Update tested mod dependencies")
    git("push", "origin", "HEAD:main")
    return git("rev-parse", "HEAD")


if __name__ == "__main__":
    sha = commit_updates(Path.cwd(), os.environ["GITHUB_SHA"])
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        out.write(f"sha={sha}\n")
