"""python tools/verify_export.py EXPORT_DIR [--expected FILE] [--events FILE ...]."""
import argparse
import json
from pathlib import Path
from evidence import read_export, verify
from metrics import observations


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--expected", type=Path)
    parser.add_argument("--events", nargs="*", type=Path, default=[])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(args.directory, args.expected)
    if args.events:
        _, files = read_export(args.directory)
        result["observations"] = observations(args.events, files["captures"])
    text = json.dumps(result, ensure_ascii=False, indent=2)
    print(text)
    if args.output:
        args.output.write_text(text + "\n", encoding="utf-8")
    return 0 if result["integrityPassed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
