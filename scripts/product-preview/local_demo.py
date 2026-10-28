"""Convenient local demo controls over the existing owned preview environment."""
import argparse
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request
import webbrowser
import zipfile
from zoneinfo import ZoneInfo

import manage


def current_state():
    state = manage.load(manage.STATE) if manage.STATE.exists() else {"status": "not-started"}
    if state["status"] in ("ready", "starting") and not manage.supervisor_running(state):
        state = {**state, "status": "supervisor-not-running"}
    return state


def check_ready():
    state = current_state()
    if state["status"] != "ready":
        raise RuntimeError("Demo is not ready: " + state["status"])
    config = manage.load(manage.CONFIG)
    base = f"http://localhost:{config['web_port']}"
    with urllib.request.urlopen(base + "/__preview", timeout=5) as response:
        owner = json.load(response)
    if owner.get("nonce") != config["nonce"] or owner.get("database") != config["database"]:
        raise RuntimeError("Preview ownership check failed; no action taken")
    for address in (base + "/login", f"http://127.0.0.1:{config['mailbox_port']}/health"):
        with urllib.request.urlopen(address, timeout=5) as response:
            if response.status != 200:
                raise RuntimeError("Demo page is unavailable")
    try:
        manage.api(config, "/api/auth/userinfo")
    except urllib.error.HTTPError as error:
        if error.code != 401:
            raise
    else:
        raise RuntimeError("Unexpected unauthenticated API response")
    return config


def manager(command):
    result = subprocess.run([sys.executable, str(Path(manage.__file__)), command],
                            cwd=manage.ROOT, capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        raise RuntimeError((result.stderr or result.stdout).strip())


def digest(path):
    with path.open("rb") as stream:
        return {"bytes": path.stat().st_size, "sha256": hashlib.file_digest(stream, "sha256").hexdigest()}


def show(config):
    print(f"演示网页：http://localhost:{config['web_port']}/login")
    print(f"测试收件箱：http://localhost:{config['mailbox_port']}")
    print("账号与演示顺序：docs/local-demo-guide.md")


def start(no_browser):
    state = current_state()
    if state["status"] not in ("ready", "starting"):
        manager("start")
        print("正在启动本机演示，沿用已有数据……", flush=True)
    else:
        print("复用现有本机演示环境。", flush=True)
    deadline = time.monotonic() + 130
    # A newly spawned worker may not have replaced its predecessor's state yet.
    grace = time.monotonic() + 5
    while time.monotonic() < deadline:
        state = current_state()
        if state["status"] == "ready":
            config = check_ready()
            print("演示环境已就绪。")
            show(config)
            if not no_browser:
                if not webbrowser.open(f"http://localhost:{config['web_port']}/login"):
                    print("浏览器未自动打开，请使用上方地址。")
            return
        if state["status"] in ("failed", "stopped", "supervisor-not-running") and time.monotonic() > grace:
            raise RuntimeError(state.get("error", "Preview stopped during startup"))
        time.sleep(.5)
    raise RuntimeError("Startup timed out; see backend/target/product-preview/*.log")


def stop():
    state = current_state()
    if state["status"] in ("ready", "starting"):
        manager("stop")
        state = current_state()
        if state["status"] != "stopped":
            raise RuntimeError("Stop not confirmed: " + state["status"])
    elif state["status"] in ("failed", "supervisor-not-running"):
        raise RuntimeError("Previous environment stopped unexpectedly; inspect its logs before recovery")
    print("演示环境已停止，账号、数据库、上传文件和测试邮件均保留。")


def backup():
    config = check_ready()
    manage.identity(config)
    if manage.mysql(config, f"SELECT nonce FROM `{config['database']}`.local_preview_guard;") != config["nonce"]:
        raise RuntimeError("Demo database ownership check failed")
    engines = manage.mysql(config, "SELECT DISTINCT ENGINE FROM information_schema.TABLES "
                           f"WHERE TABLE_SCHEMA='{config['database']}' AND TABLE_TYPE='BASE TABLE';")
    if set(engines.splitlines()) != {"InnoDB"}:
        raise RuntimeError("Consistent snapshot requires only InnoDB tables")
    if subprocess.check_output(["git", "status", "--porcelain"], cwd=manage.ROOT, text=True).strip():
        raise RuntimeError("Save tracked source changes in Git before creating a versioned demo backup")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=manage.ROOT, text=True).strip()
    destination = manage.ROOT / ".demo-backups" / (datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%Y%m%d-%H%M%S") + "-" + commit[:7])
    destination.mkdir(parents=True, exist_ok=False)
    sql = destination / "demo.sql"
    env = os.environ.copy()
    env["MYSQL_PWD"] = config["root_password"]
    command = [str(manage.MYSQL_HOME / "bin/mysqldump.exe"), "--no-defaults", "--protocol=TCP",
               "--host=127.0.0.1", f"--port={config['mysql_port']}", "--user=root", "--default-character-set=utf8mb4",
               "--single-transaction", "--quick", "--routines", "--triggers", "--events", "--hex-blob",
               "--set-gtid-purged=OFF", "--no-tablespaces", "--column-statistics=0", "--databases", config["database"],
               "--result-file=" + str(sql)]
    result = subprocess.run(command, env=env, capture_output=True, text=True, encoding="utf-8", creationflags=manage.NO_WINDOW)
    if result.returncode or not sql.exists() or sql.stat().st_size == 0:
        raise RuntimeError("Database snapshot failed; incomplete directory: " + str(destination))
    subprocess.run(["git", "bundle", "create", str(destination / "source.bundle"), "HEAD"], cwd=manage.ROOT, check=True,
                   capture_output=True)
    archive_path = destination / "runtime.zip"
    sources = [(manage.HERE / "uploads", "uploads"), (manage.ROOT / "frontend/dist", "frontend-dist"),
               (manage.HERE / "backend-runtime.jar", "backend-runtime.jar"), (manage.CONFIG, "config.json"),
               (manage.HERE / "mailbox.json", "mailbox.json")]
    with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
        for source, name in sources:
            if not source.exists():
                raise RuntimeError("Missing demo artifact: " + str(source))
            if source.is_symlink() or source.is_junction():
                raise RuntimeError("Refusing to archive a reparse point")
            if source.is_file():
                archive.write(source, name)
                continue
            for folder, directories, files in os.walk(source, followlinks=False):
                for child in directories + files:
                    path = Path(folder) / child
                    if path.is_symlink() or path.is_junction():
                        raise RuntimeError("Refusing to archive a reparse point")
                for filename in files:
                    path = Path(folder) / filename
                    archive.write(path, (Path(name) / path.relative_to(source)).as_posix())
    with zipfile.ZipFile(archive_path) as archive:
        if archive.testzip() is not None:
            raise RuntimeError("Runtime archive integrity check failed")
    subprocess.run(["git", "bundle", "verify", str(destination / "source.bundle")], cwd=manage.ROOT, check=True, capture_output=True)
    manifest = {"status": "complete", "at": datetime.now(ZoneInfo("Asia/Shanghai")).isoformat(),
                "commit": commit, "run_id": config["run_id"], "database": config["database"],
                "scope": "Local demo only; SQL + uploads + builds + mailbox + private config + Git history",
                "note": "Pause demo edits during backup. Restore is manual; not a portable installer or one-click reset.",
                "files": {path.name: digest(path) for path in (sql, destination / "source.bundle", archive_path)}}
    manage.save(destination / "manifest.json", manifest)
    manage.save(manage.ROOT / ".demo-backups/latest.json", {"path": str(destination), "commit": commit, "at": manifest["at"]})
    print("本机演示备份已保存：" + str(destination))
    print("备份含本地账号配置，请保存在当前电脑；恢复由维护者按说明处理。")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("start", "stop", "status", "backup"))
    parser.add_argument("--no-browser", action="store_true")
    args = parser.parse_args()
    try:
        if args.command == "start":
            start(args.no_browser)
        elif args.command == "stop":
            stop()
        elif args.command == "backup":
            backup()
        else:
            state = current_state()
            print("演示状态：" + state["status"])
            if state["status"] == "ready":
                show(check_ready())
            elif state["status"] not in ("not-started", "stopped"):
                raise RuntimeError(state.get("error", "Demo is not ready"))
    except (OSError, RuntimeError, ValueError, subprocess.SubprocessError) as error:
        print("操作未完成：" + str(error), file=sys.stderr)
        print("日志目录：backend/target/product-preview/；没有重置数据。", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
