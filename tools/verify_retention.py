"""Compare every pre-existing business row across stopped DB/WAL backups."""
import argparse
import json
import sqlite3
import tarfile
import tempfile
from contextlib import ExitStack, closing
from pathlib import Path


def open_backup(stack, archive):
    directory = Path(stack.enter_context(tempfile.TemporaryDirectory(prefix="m71-retention-")))
    with tarfile.open(archive) as source:
        source.extractall(directory, filter="data")
    db = stack.enter_context(closing(sqlite3.connect(directory / "databases/m71-demo.db")))
    db.row_factory = sqlite3.Row
    return db, directory


def compare_table(before, after, name):
    columns = list(before.execute(f'PRAGMA table_info("{name}")'))
    keys = [row["name"] for row in sorted(columns, key=lambda r: r["pk"]) if row["pk"]]
    assert keys, f"No primary key in {name}"
    clause = " AND ".join(f'"{key}"=?' for key in keys)
    count = 0
    for original in before.execute(f'SELECT * FROM "{name}"'):
        current = after.execute(f'SELECT * FROM "{name}" WHERE {clause}', tuple(original[k] for k in keys)).fetchone()
        assert current is not None, f"Missing old row in {name}"
        if name == "local_device_state":
            assert current["id"] == original["id"]
            for key in ("nextCaptureSeq", "nextAuditSeq", "nextFixtureSerial"):
                assert current[key] >= original[key], f"Counter regressed: {key}"
        else:
            assert dict(current) == dict(original), f"Old row changed in {name}: {[original[k] for k in keys]}"
        count += 1
    return count


def verify(before_archive, after_archive):
    with ExitStack() as stack:
        before, old_dir = open_backup(stack, before_archive)
        after, new_dir = open_backup(stack, after_archive)
        assert after.execute("PRAGMA integrity_check").fetchall()[0][0] == "ok"
        assert after.execute("PRAGMA foreign_key_check").fetchall() == []
        assert before.execute("PRAGMA user_version").fetchone()[0] == after.execute("PRAGMA user_version").fetchone()[0]
        tables = [r[0] for r in before.execute("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'local_%'")]
        counts = {table: compare_table(before, after, table) for table in tables}
        draft = "files/key-calibration/draft.json"
        assert (old_dir / draft).read_bytes() == (new_dir / draft).read_bytes(), "Physical key draft changed"
        assert not (new_dir / "files/key-calibration/active.json").exists(), "Unexpected verified key profile"
        return {"baselineFactsPreserved": True, "rowsCompared": counts,
                "deviceCountersMonotonic": True, "keyDraftPreserved": True,
                "activePhysicalKeyProfile": False, "integrity": "ok", "foreignKeys": []}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = verify(args.before, args.after)
    args.output.write_text(json.dumps(result, indent=2))
    print(json.dumps(result))
