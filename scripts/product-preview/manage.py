"""Local-only test environment. Stop preserves its isolated data; no business DB access."""
import ctypes
from ctypes import wintypes
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import traceback
import urllib.error
import urllib.request
import uuid

sys.stdout.reconfigure(encoding="utf-8")
sys.stderr.reconfigure(encoding="utf-8")
SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parents[1]
HERE = ROOT / "backend/target/product-preview"
HERE.mkdir(parents=True, exist_ok=True)
CONFIG = HERE / "config.json"
STATE = HERE / "state.json"
STOP = HERE / "stop.request"
MYSQL_HOME = Path(r"C:\Program Files\MySQL\MySQL Server 8.0")
JAVA = Path.home() / ".jdks/ms-21.0.9/bin/java.exe"
NODE = Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe"
NO_WINDOW = subprocess.CREATE_NO_WINDOW
kernel = ctypes.WinDLL("kernel32", use_last_error=True)
kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
kernel.OpenProcess.restype = wintypes.HANDLE
kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
kernel.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
kernel.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
kernel.CloseHandle.argtypes = [wintypes.HANDLE]

def save(path, data):
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    tmp.replace(path)

def load(path):
    return json.loads(path.read_text(encoding="utf-8"))

class OwnedProcess:
    def __init__(self, pid, exe, earliest=0):
        self.pid = pid
        self.handle = kernel.OpenProcess(0x100000 | 0x1000 | 1, False, pid)
        if not self.handle:
            raise RuntimeError("Cannot capture owned process")
        try:
            text = ctypes.create_unicode_buffer(32768)
            size = wintypes.DWORD(len(text))
            times = [wintypes.FILETIME() for _ in range(4)]
            if not kernel.QueryFullProcessImageNameW(self.handle, 0, text, ctypes.byref(size)) or not kernel.GetProcessTimes(self.handle, *[ctypes.byref(t) for t in times]):
                raise RuntimeError("Cannot identify process")
            self.created = ((times[0].dwHighDateTime << 32) + times[0].dwLowDateTime) / 10000000 - 11644473600
            if Path(text.value).resolve() != Path(exe).resolve() or self.created < earliest - 2:
                raise RuntimeError("Process does not belong to this environment")
        except Exception:
            kernel.CloseHandle(self.handle)
            raise
    def alive(self):
        return kernel.WaitForSingleObject(self.handle, 0) == 258
    def close(self, kill=False):
        if kill and self.alive():
            if not kernel.TerminateProcess(self.handle, 1):
                raise RuntimeError("Cannot stop captured process")
            if kernel.WaitForSingleObject(self.handle, 10000) != 0:
                raise RuntimeError("Captured process did not stop")
        kernel.CloseHandle(self.handle)

def require_path(config):
    expected = Path(r"C:\Windows\Temp") / ("coursehub-product-preview-" + config["run_id"])
    if not re.fullmatch(r"[a-f0-9]{32}", config["run_id"]) or Path(config["data_root"]) != expected:
        raise RuntimeError("Unexpected test data path")
    if config["database"] != "coursehub_product_preview_" + config["run_id"] or not re.fullmatch(r"[a-f0-9]{32}", config["nonce"]):
        raise RuntimeError("Unexpected preview database identity")
    if expected.is_symlink() or expected.is_junction() or (expected / "data").is_symlink() or (expected / "data").is_junction():
        raise RuntimeError("Test data path must not be a reparse point")
    if (expected / ".coursehub-run-marker").read_text(encoding="utf-8") != config["nonce"]:
        raise RuntimeError("Test data ownership mismatch")
    return expected

def mysql(config, sql, password=True):
    env = os.environ.copy()
    env.pop("MYSQL_PWD", None)
    if password:
        env["MYSQL_PWD"] = config["root_password"]
    command = [str(MYSQL_HOME / "bin/mysql.exe"), "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", f"--port={config['mysql_port']}", "--user=root", "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names", "--connect-timeout=2"]
    if not password:
        command.append("--password=")
    result = subprocess.run(command, input=sql, text=True, encoding="utf-8", capture_output=True, timeout=60, env=env, creationflags=NO_WINDOW)
    if result.returncode:
        raise RuntimeError("Local SQL failed: " + result.stderr.strip())
    return result.stdout.strip()

def identity(config, password=True):
    root = require_path(config)
    parts = mysql(config, "SELECT @@datadir,@@port,@@version,@@pid_file;", password=password).split("\t")
    if len(parts) != 4 or Path(parts[0]).resolve() != (root / "data").resolve() or int(parts[1]) != config["mysql_port"] or not parts[2].startswith("8.") or Path(parts[3]).resolve() != (root / "mysql.pid").resolve():
        raise RuntimeError("Refusing operation on an unexpected MySQL instance")
    return int((root / "mysql.pid").read_text(encoding="utf-8").strip())

def api(config, path, body=None, token=None, method=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(f"http://127.0.0.1:{config['api_port']}" + path, data=data, headers=headers, method=method or ("POST" if body is not None else "GET"))
    with urllib.request.urlopen(request, timeout=8) as response:
        result = json.load(response)
    if result["code"] != 200:
        raise RuntimeError("Local API rejected request: " + str(result.get("message")))
    return result["data"]

def start_program(command, cwd, label, env=None):
    log = open(HERE / (label + ".log"), "ab", buffering=0)
    process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, creationflags=NO_WINDOW)
    owned = OwnedProcess(process.pid, command[0], time.time() - 2)
    return process, owned, log

def supervisor_running(state):
    try:
        owned = OwnedProcess(state["supervisor_pid"], sys.executable)
        try:
            return owned.alive() and ("supervisor_created" not in state or abs(owned.created - state["supervisor_created"]) < .01)
        finally:
            owned.close()
    except (OSError, RuntimeError):
        return False

def wait_for(check, seconds, label):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if STOP.exists():
            raise RuntimeError("Startup cancelled")
        try:
            if check():
                return
        except (OSError, ValueError, RuntimeError):
            pass
        time.sleep(.3)
    raise RuntimeError(label + " not ready; inspect local logs")

def worker():
    config = load(CONFIG)
    programs = []
    mysql_owned = None
    state = {"status": "starting", "supervisor_pid": os.getpid(), "run_id": config["run_id"], "frontend_url": f"http://localhost:{config['web_port']}", "backend_url": f"http://127.0.0.1:{config['api_port']}", "database": config["database"], "mysql_port": config["mysql_port"], "git_commit": config["git_commit"], "mailbox_url": f"http://localhost:{config['mailbox_port']}"}
    own = OwnedProcess(os.getpid(), sys.executable)
    state["supervisor_created"] = own.created
    own.close()
    save(STATE, state)
    try:
        root = require_path(config)
        if not (root / "data/mysql").exists():
            with open(HERE / "mysql-initialize.log", "ab", buffering=0) as log:
                process = subprocess.Popen([str(MYSQL_HOME / "bin/mysqld.exe"), "--no-defaults", "--basedir=" + MYSQL_HOME.as_posix(), "--datadir=" + (root / "data").as_posix(), "--initialize-insecure", "--console"], stdout=log, stderr=subprocess.STDOUT, creationflags=NO_WINDOW)
                wait_for(lambda: process.poll() is not None, 60, "MySQL initializer")
                if process.returncode != 0:
                    raise RuntimeError("MySQL initialization failed")
        earliest = time.time()
        mysql_program = start_program([str(MYSQL_HOME / "bin/mysqld.exe"), "--no-defaults", "--basedir=" + MYSQL_HOME.as_posix(), "--datadir=" + (root / "data").as_posix(), "--pid-file=" + (root / "mysql.pid").as_posix(), "--bind-address=127.0.0.1", f"--port={config['mysql_port']}", "--mysqlx=0", "--skip-log-bin", "--innodb-buffer-pool-size=64M", "--console"], ROOT, "mysql")
        programs.append(mysql_program)
        wait_for(lambda: mysql(config, "SELECT 1;", password=config.get("password_set", False)) == "1", 60, "MySQL")
        mysql_owned = OwnedProcess(identity(config, password=config.get("password_set", False)), MYSQL_HOME / "bin/mysqld.exe", earliest)
        if not config.get("password_set"):
            mysql(config, "ALTER USER 'root'@'localhost' IDENTIFIED BY '" + config["root_password"] + "';", password=False)
            config["password_set"] = True
            save(CONFIG, config)
        if not config.get("schema_ready"):
            database = config["database"]
            sql = (ROOT / "backend/src/main/resources/db/init.sql").read_text(encoding="utf-8-sig").replace("bgss_xtt", database)
            targets = re.findall(r"(?im)^\s*(?:CREATE DATABASE(?: IF NOT EXISTS)?|USE|DROP DATABASE(?: IF EXISTS)?)\s+([\w`]+)", sql)
            if not targets or any(name.strip('`') != database for name in targets):
                raise RuntimeError("SQL database scope mismatch")
            identity(config)
            mysql(config, sql)
            for filename in ["2026-10-10-account-recovery.sql", "2026-10-11-private-chat.sql"]:
                identity(config)
                mysql(config, f"USE `{database}`;\n" + (ROOT / "backend/src/main/resources/db/migrations" / filename).read_text(encoding="utf-8-sig"))
            mysql(config, f"USE `{database}`; CREATE TABLE local_preview_guard(nonce CHAR(32) PRIMARY KEY); INSERT INTO local_preview_guard VALUES('{config['nonce']}'); CREATE USER 'product_preview'@'localhost' IDENTIFIED BY '{config['db_password']}'; GRANT ALL PRIVILEGES ON `{database}`.* TO 'product_preview'@'localhost';")
            config["schema_ready"] = True
            save(CONFIG, config)
        if mysql(config, f"SELECT nonce FROM `{config['database']}`.local_preview_guard;") != config["nonce"]:
            raise RuntimeError("Database nonce mismatch")
        programs.append(start_program([str(NODE), str(SCRIPTS / "servers.mjs"), str(CONFIG)], ROOT, "web-mailbox"))
        def servers_ready():
            with urllib.request.urlopen(f"http://127.0.0.1:{config['mailbox_port']}/health", timeout=3) as response:
                return response.status == 200
        wait_for(servers_ready, 20, "Web and mailbox")
        env = os.environ.copy()
        env.update({"DB_URL": f"jdbc:mysql://127.0.0.1:{config['mysql_port']}/{config['database']}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai", "DB_USERNAME": "product_preview", "DB_PASSWORD": config["db_password"], "JWT_SECRET": config["jwt_secret"], "RECOVERY_ENABLED": "true", "RECOVERY_HMAC_SECRET": config["recovery_secret"], "SMS_MOCK": "false", "SMTP_HOST": "127.0.0.1", "SMTP_PORT": str(config["smtp_port"]), "SMTP_USERNAME": "", "SMTP_PASSWORD": "", "SMTP_FROM": "coursehub@example.test", "SMTP_STARTTLS": "false", "SMTP_SSL": "false", "FILE_UPLOAD_PATH": str(HERE / "uploads")})
        # Run an owned snapshot: rebuilding target/ must not rewrite a live JVM's jar.
        jar = HERE / "backend-runtime.jar"
        shutil.copy2(ROOT / "backend/target/bugaoshan-backend-0.0.1-SNAPSHOT.jar", jar)
        programs.append(start_program([str(JAVA), "-Dfile.encoding=UTF-8", "-jar", str(jar), "--server.address=127.0.0.1", f"--server.port={config['api_port']}", "--spring.mail.properties.mail.smtp.auth=false", "--logging.level.root=WARN", "--spring.main.banner-mode=off"], ROOT / "backend", "backend", env))
        def backend_ready():
            try:
                api(config, "/api/auth/userinfo")
            except urllib.error.HTTPError as error:
                return error.code == 401
            return False
        wait_for(backend_ready, 75, "Backend")
        if not config.get("accounts_created"):
            for account in config["accounts"]:
                exists = mysql(config, f"SELECT COUNT(*) FROM `{config['database']}`.user WHERE username='{account['username']}';")
                if exists == "0":
                    api(config, "/api/auth/register", {"username": account["username"], "password": account["password"], "nickname": account["nickname"], "department": "产品体验学院"})
                account["user_id"] = int(mysql(config, f"SELECT user_id FROM `{config['database']}`.user WHERE username='{account['username']}';"))
            from seed import seed_preview
            identity(config)
            seed_preview(config, HERE, lambda sql: mysql(config, sql))
            config["accounts_created"] = True
            save(CONFIG, config)
        def frontend_ready():
            with urllib.request.urlopen(f"http://127.0.0.1:{config['web_port']}/login", timeout=3) as response:
                return response.status == 200
        wait_for(frontend_ready, 20, "Frontend")
        state.update(status="ready", mysql_pid=mysql_owned.pid, backend_pid=programs[-1][0].pid, web_mailbox_pid=programs[-2][0].pid, accounts=config["accounts"])
        save(STATE, state)
        while not STOP.exists():
            if not mysql_owned.alive() or any(not record[1].alive() for record in programs[1:]):
                raise RuntimeError("A test service stopped; inspect local logs")
            time.sleep(1)
    except Exception as error:
        state.update(status="failed", error=str(error))
        traceback.print_exc()
        save(STATE, state)
    finally:
        cleanup_errors = []
        for process, owned, log in reversed(programs):
            if 'mysql_program' in locals() and process is mysql_program[0]:
                continue
            try:
                owned.close(kill=True)
                process.wait(timeout=10)
            except Exception as error:
                cleanup_errors.append(str(error))
            log.close()
        try:
            if mysql_owned and mysql_owned.alive():
                identity(config)
                mysql(config, "SHUTDOWN;")
                if kernel.WaitForSingleObject(mysql_owned.handle, 10000) != 0:
                    mysql_owned.close(kill=True)
                    mysql_owned = None
            if mysql_owned:
                mysql_owned.close()
            if 'mysql_program' in locals():
                mysql_program[1].close(kill=True)
                mysql_program[2].close()
        except Exception as error:
            cleanup_errors.append(str(error))
        if state["status"] != "failed":
            state["status"] = "stopped"
        if cleanup_errors:
            state["cleanup_errors"] = cleanup_errors
        save(STATE, state)

def main():
    command = sys.argv[1] if len(sys.argv) > 1 else "status"
    if command == "worker":
        worker()
    elif command == "status":
        state = load(STATE) if STATE.exists() else {"status": "not-started"}
        if state["status"] in ["starting", "ready"] and not supervisor_running(state):
            state.update(status="supervisor-not-running", error="Inspect local logs and owned service ports before restarting")
        print(json.dumps(state, ensure_ascii=False, indent=2))
    elif command == "stop":
        STOP.write_text("stop this environment\n", encoding="utf-8")
        for attempt in range(40):
            if not STATE.exists() or not supervisor_running(load(STATE)):
                break
            time.sleep(.5)
        print(json.dumps(load(STATE) if STATE.exists() else {"status": "not-started"}, ensure_ascii=False, indent=2))
    elif command == "prepare-material-check":
        config = load(CONFIG)
        identity(config)
        name, nonce = "coursehub_material_check_" + uuid.uuid4().hex, uuid.uuid4().hex
        mysql(config, f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4; CREATE TABLE `{name}`.material_test_guard(nonce CHAR(32) PRIMARY KEY); INSERT INTO `{name}`.material_test_guard VALUES('{nonce}');")
        values = {"COURSEHUB_MATERIAL_MYSQL_URL": f"jdbc:mysql://127.0.0.1:{config['mysql_port']}/{name}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai", "COURSEHUB_MATERIAL_MYSQL_USER": "root", "COURSEHUB_MATERIAL_MYSQL_PASSWORD": config["root_password"], "COURSEHUB_MATERIAL_MYSQL_NONCE": nonce}
        save(HERE / "material-check-env.json", values)
        print("Created guarded material IT database. Environment saved in ignored runtime/material-check-env.json.")
    elif command == "start":
        for artifact in [ROOT / "backend/target/bugaoshan-backend-0.0.1-SNAPSHOT.jar", ROOT / "frontend/dist/index.html", JAVA, NODE, MYSQL_HOME / "bin/mysqld.exe"]:
            if not artifact.is_file():
                raise RuntimeError("Required build/runtime missing: " + str(artifact))
        if STATE.exists():
            existing = load(STATE)
            if existing["status"] in ["starting", "ready"] and supervisor_running(existing):
                raise RuntimeError("Environment is already starting or running")
        for port in ([load(CONFIG)[key] for key in ["web_port", "api_port", "mysql_port", "mailbox_port", "smtp_port"]] if CONFIG.exists() else [3001, 8081, 33363, 3002, 2526]):
            with socket.socket() as check:
                check.bind(("127.0.0.1", port))
        if not CONFIG.exists():
            run_id, nonce = uuid.uuid4().hex, uuid.uuid4().hex
            data_root = Path(r"C:\Windows\Temp") / ("coursehub-product-preview-" + run_id)
            data_root.mkdir(exist_ok=False)
            (data_root / "data").mkdir()
            (data_root / ".coursehub-run-marker").write_text(nonce, encoding="utf-8")
            commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, encoding="utf-8").strip()
            save(CONFIG, {"run_id": run_id, "nonce": nonce, "data_root": str(data_root), "database": "coursehub_product_preview_" + run_id, "mysql_port": 33363, "api_port": 8081, "web_port": 3001, "mailbox_port": 3002, "smtp_port": 2526, "recovery_secret": secrets.token_urlsafe(64), "root_password": secrets.token_hex(24), "db_password": secrets.token_hex(24), "jwt_secret": secrets.token_urlsafe(64), "git_commit": commit, "accounts": [{"username": name, "password": "Product-local-2026!", "nickname": nick, "user_type": role, "email": name + "@example.test"} for name, nick, role in [("product_student_a", "体验学生甲", 1), ("product_student_b", "体验学生乙", 1), ("product_teacher", "体验教师", 2), ("product_admin", "体验管理员", 3)]]})
        STOP.unlink(missing_ok=True)
        config = load(CONFIG)
        config["web_url"] = f"http://localhost:{config['web_port']}"
        save(CONFIG, config)
        with open(HERE / "supervisor.log", "ab", buffering=0) as log:
            process = subprocess.Popen([sys.executable, str(__file__), "worker"], cwd=HERE, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, creationflags=NO_WINDOW | subprocess.DETACHED_PROCESS | subprocess.CREATE_NEW_PROCESS_GROUP)
        print("Local test supervisor started: " + str(process.pid))
    else:
        raise RuntimeError("Use start, status, stop or prepare-material-check")

if __name__ == "__main__":
    main()
