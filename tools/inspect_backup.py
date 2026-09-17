"""Inspect a stopped Demo DB backup in an isolated temporary directory."""
import json
import sqlite3
import sys
import tarfile
import tempfile
from pathlib import Path


def inspect(backup):
    with tempfile.TemporaryDirectory(prefix="m71-demo-check-") as directory:
        target = Path(directory)
        with tarfile.open(backup) as archive:
            archive.extractall(target, filter="data")
        db = sqlite3.connect(target / "databases" / "m71-demo.db")
        try:
            result = {"integrity": db.execute("PRAGMA integrity_check").fetchall(),
                      "foreignKeys": db.execute("PRAGMA foreign_key_check").fetchall(),
                      "version": db.execute("PRAGMA user_version").fetchone()[0],
                      "roomIdentity": db.execute("SELECT * FROM room_master_table").fetchall(),
                      "auditIndexes": db.execute("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='local_operation_event'").fetchall()}
            for table in ("local_order", "local_input_capture", "local_scan", "local_barcode_guard", "local_operation_event"):
                result[table] = db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            return result
        finally:
            db.close()


if __name__ == "__main__":
    print(json.dumps(inspect(sys.argv[1]), ensure_ascii=False, indent=2))
