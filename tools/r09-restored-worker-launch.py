"""Seed a fresh restored-worker lab from an immutable stopped source and run the Java driver."""
import configparser
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys


ROOT = Path(os.environ.get("E2A_CROSS_ROOT", "D:/acrylic-pda-outbound")).resolve()
ATTEMPT = Path(os.environ["E2A_CROSS_ATTEMPT"]).resolve()
SOURCE = Path(os.environ.get("E2A_RESTORED_SOURCE", str(ROOT / ".local/e2a-cross-20260925-667"))).resolve()
MYSQL = Path("C:/Program Files/MySQL/MySQL Server 8.0/bin")


def digest(path):
    return hashlib.file_digest(Path(path).open("rb"), "sha256").hexdigest()


def manifest(root):
    rows = []
    for path in sorted(p for p in root.rglob("*") if p.is_file()):
        rows.append({"path": str(path.relative_to(root)).replace("\\", "/"),
                     "length": path.stat().st_size, "sha256": digest(path)})
    return rows


def save(path, value):
    Path(path).write_text(json.dumps(value, indent=2, ensure_ascii=True), encoding="utf-8")


def identity(attempt):
    config = configparser.ConfigParser(interpolation=None)
    config.read(attempt / "private/client.cnf")
    client = config["client"]
    database = os.environ["E2A_CROSS_DATABASE"]
    port = int(os.environ["E2A_CROSS_PORT"])
    with __import__("pymysql").connect(host="127.0.0.1", port=port, user=client["user"],
                                        password=client["password"], database=database) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT @@datadir,@@port,DATABASE(),CURRENT_USER(),@@log_bin")
            row = cursor.fetchone()
            expected = (str(attempt / "data"), port, database, client["user"] + "@127.0.0.1", 1)
            actual = (str(Path(row[0]).resolve()), row[1], row[2], row[3], row[4])
            if actual != (str(Path(expected[0]).resolve()), *expected[1:]):
                raise RuntimeError(f"isolated identity mismatch: {actual}")
            cursor.execute("SHOW TABLES")
            if cursor.fetchall():
                raise RuntimeError("fresh schema is not empty before source import")
    return client


def import_source(client, attempt):
    database = os.environ["E2A_CROSS_DATABASE"]
    port = os.environ["E2A_CROSS_PORT"]
    command = [str(MYSQL / "mysql.exe"), "--defaults-file=" + str(attempt / "private/client.cnf"),
               "--protocol=TCP", "--host=127.0.0.1", "--port=" + port, "--binary-mode",
               "--default-character-set=utf8mb4", database]
    log = (attempt / "private/import-source.log").open("wb")
    try:
        with (SOURCE / "private/archive-snapshot.sql").open("rb") as source:
            result = subprocess.run(command, stdin=source, stdout=log, stderr=subprocess.STDOUT, timeout=180)
    finally:
        log.close()
    if result.returncode != 0:
        raise RuntimeError("source archive import failed")
    shutil.copytree(SOURCE / "storage", attempt / "storage", dirs_exist_ok=True)
    for name in ("server-private.der", "server-public.der", "formal-http-input.json"):
        shutil.copy2(SOURCE / "private" / name, attempt / "private" / name)
    shutil.copy2(SOURCE / "private/archive-snapshot-facts.json", attempt / "private/original667-facts.json")
    return {"source": str(SOURCE), "archive_sha256": digest(SOURCE / "private/archive-snapshot.sql"),
            "storage_files": len([p for p in (attempt / "storage").rglob("*") if p.is_file()]),
            "client_user": client["user"]}


def run_driver(attempt):
    (attempt / "tmp").mkdir(parents=True, exist_ok=True)
    classpath = json.loads(Path(os.environ["E2A_RESTORED_CLASSPATH"]).read_text(encoding="utf-8"))
    java = Path(os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-21.0.11")) / "bin/java.exe"
    command = [str(java), "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC",
               "-Djava.io.tmpdir=" + str(attempt / "tmp"), "-cp", os.pathsep.join(classpath),
               "com.acrylic.outbound.e2averify.E2aRestoredWorkerRestore"]
    save(attempt / "launch-command.json", {"args": command, "cwd": str(ROOT / "server")})
    environment = os.environ.copy()
    environment["TEMP"] = str(attempt / "tmp")
    environment["TMP"] = str(attempt / "tmp")
    environment["JAVA_TOOL_OPTIONS"] = "-Djava.io.tmpdir=" + str(attempt / "tmp")
    with (attempt / "integration.log").open("wb") as log:
        result = subprocess.run(command, cwd=ROOT / "server", env=environment,
                                stdout=log, stderr=subprocess.STDOUT, timeout=420)
    return result.returncode


def main():
    if not SOURCE.is_dir() or SOURCE == ATTEMPT or not (SOURCE / "private/archive-snapshot.sql").is_file():
        raise RuntimeError("immutable restored-worker source is missing or invalid")
    if not ATTEMPT.is_relative_to(ROOT):
        raise RuntimeError("attempt escapes workspace")
    before = manifest(SOURCE)
    save(ATTEMPT / "source-manifest-before.json", before)
    client = identity(ATTEMPT)
    imported = import_source(client, ATTEMPT)
    save(ATTEMPT / "source-import.json", imported)
    save(ATTEMPT / "source-manifest-after.json", manifest(SOURCE))
    if before != manifest(SOURCE):
        raise RuntimeError("immutable source changed during import")
    code = run_driver(ATTEMPT)
    save(ATTEMPT / "launcher-result.json", {"exit": code, "sourceUnchanged": True})
    return code


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as error:
        save(ATTEMPT / "launcher-result.json", {"exit": 1, "sourceUnchanged": False,
                                                 "error": str(error)})
        raise
