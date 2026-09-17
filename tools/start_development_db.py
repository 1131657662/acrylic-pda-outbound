"""Start a workspace-only MySQL instance. Never reads or changes existing MySQL services."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
LOCAL = ROOT / ".local"
MYSQL_BIN = Path(os.environ.get("OUTBOUND_MYSQL_BIN", r"C:\Program Files\MySQL\MySQL Server 8.0\bin"))
PORT = 13316


def mysql(sql, option_file=None):
    command = [str(MYSQL_BIN / "mysql.exe")]
    if option_file:
        command.append(f"--defaults-extra-file={option_file}")
    else:
        command += ["--no-defaults", "-h127.0.0.1", f"-P{PORT}", "-uroot"]
    return subprocess.run(command, input=sql.encode(), capture_output=True, timeout=30)


def initialize():
    data = LOCAL / "mysql" / "data"
    if (data / "auto.cnf").exists():
        return data
    data.mkdir(parents=True, exist_ok=True)
    result = subprocess.run([str(MYSQL_BIN / "mysqld.exe"), "--no-defaults", "--initialize-insecure",
                             f"--basedir={MYSQL_BIN.parent}", f"--datadir={data}"],
                            capture_output=True, timeout=60)
    if result.returncode:
        raise RuntimeError("Workspace MySQL initialization failed; inspect .local/mysql/data/*.err")
    return data


def start(data):
    config = LOCAL / "development.json"
    if config.exists() and mysql("SELECT 1;", LOCAL / "mysql-client.cnf").returncode == 0:
        return
    log = (LOCAL / "mysql" / "process.log").open("ab")
    args = [str(MYSQL_BIN / "mysqld.exe"), "--no-defaults", f"--basedir={MYSQL_BIN.parent}",
            f"--datadir={data}", "--bind-address=127.0.0.1", f"--port={PORT}",
            "--mysqlx=OFF", "--innodb-buffer-pool-size=134217728", "--max-connections=30",
            "--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_as_cs",
            "--default-time-zone=+00:00", "--innodb-flush-log-at-trx-commit=1"]
    subprocess.Popen(args, stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
    log.close()
    for _ in range(40):
        options = LOCAL / "mysql-client.cnf" if config.exists() else None
        if mysql("SELECT 1;", options).returncode == 0:
            return
        time.sleep(.5)
    raise RuntimeError("Workspace MySQL failed to start; inspect .local/mysql/process.log")


def provision():
    path = LOCAL / "development.json"
    if path.exists():
        return
    settings = {"dbPassword": secrets.token_urlsafe(24), "rootPassword": secrets.token_urlsafe(24),
                "adminUsername": "admin", "adminPassword": secrets.token_urlsafe(18), "dbPort": PORT}
    # Generated credentials contain only URL-safe ASCII, never user-provided SQL.
    sql = f"""
        CREATE DATABASE acrylic_outbound_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
        CREATE DATABASE acrylic_outbound_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
        CREATE USER 'outbound_local'@'127.0.0.1' IDENTIFIED BY '{settings['dbPassword']}';
        GRANT ALL ON acrylic_outbound_dev.* TO 'outbound_local'@'127.0.0.1';
        GRANT ALL ON acrylic_outbound_test.* TO 'outbound_local'@'127.0.0.1';
        ALTER USER 'root'@'localhost' IDENTIFIED BY '{settings['rootPassword']}';
    """
    result = mysql(sql)
    if result.returncode:
        raise RuntimeError("Local provisioning failed; credentials were not printed")
    path.write_text(json.dumps(settings, indent=2) + "\n", encoding="utf-8")
    (LOCAL / "mysql-client.cnf").write_text(
        f"[client]\nhost=127.0.0.1\nport={PORT}\nuser=root\npassword={settings['rootPassword']}\n", encoding="utf-8")


def verify_instance(data):
    options = LOCAL / "mysql-client.cnf" if (LOCAL / "development.json").exists() else None
    result = mysql("SELECT @@datadir;", options)
    lines = result.stdout.decode(errors="strict").strip().splitlines()
    if result.returncode or len(lines) != 2 or Path(lines[1]).resolve() != data.resolve():
        raise RuntimeError("Port does not identify this workspace datadir; refusing to provision any database")


if __name__ == "__main__":
    LOCAL.mkdir(exist_ok=True)
    workspace_data = initialize()
    start(workspace_data)
    verify_instance(workspace_data)
    provision()
    print(f"Workspace MySQL ready on 127.0.0.1:{PORT}. Credentials: .local/development.json (git-ignored).")
