"""Explicit offline P5 command: external raw key files -> binary pipe -> independent read-back."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

from recovery_rekey_verify import canonical, read, require, verify


def checked_file(path):
    require(path.is_absolute() and not any(p in (".", "..") for p in path.parts), "REKEY_EXTERNAL_FILE")
    for item in (path, *path.parents):
        require(not item.is_symlink() and not item.is_junction(), "REKEY_PATH_REDIRECTION")
    require(path.is_file() and path.stat().st_nlink == 1, "REKEY_EXTERNAL_FILE")
    return path.resolve(strict=True)


def external(path, request):
    roots = [request[name] for name in ("authorityRoot", "leaseRoot", "closedAuthority")]
    roots.extend(request["restoreRoots"])
    for item in roots:
        root = Path(item).resolve()
        require(not path.is_relative_to(root) and not root.is_relative_to(path), "REKEY_EXTERNAL_INPUT")


def key_file(path, request):
    path = checked_file(path)
    external(path, request)
    with path.open("rb") as stream:
        key = bytearray(stream.read(33))
    require(len(key) == 32, "REKEY_KEY_LENGTH")
    return key


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("java", "classpath", "request", "current-key-file", "next-key-file", "evidence-dir"):
        parser.add_argument("--" + name, type=Path, required=True)
    return parser.parse_args()


def command(args):
    classpath_file = checked_file(args.classpath)
    entries = json.loads(classpath_file.read_text(encoding="utf-8"))
    require(isinstance(entries, list) and entries and all(isinstance(p, str) and Path(p).is_absolute()
            and Path(p).exists() for p in entries), "REKEY_CLASSPATH")
    return [str(checked_file(args.java)), "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(entries),
            "com.acrylic.outbound.recovery.RecoveryRekeyCli", "--request", str(args.request)]


def invoke(cmd, keys, directory):
    with (directory / "stdout.json").open("xb") as output, (directory / "stderr.txt").open("xb") as error:
        process = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=output, stderr=error)
        try:
            process.communicate(input=keys, timeout=30)
            return process.returncode
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()
            return 3


def verified_receipt(request, keys, directory):
    receipt = read(directory / "stdout.json")
    report = verify(request, receipt, *keys)
    (directory / "verified.json").write_bytes(canonical(report))
    print(canonical(report).decode("utf-8"), end="")


def execute(args):
    args.request = checked_file(args.request)
    request = read(args.request)
    external(args.request, request)
    directory = args.evidence_dir
    require(directory.is_absolute() and not directory.exists(), "REKEY_NEW_EVIDENCE_DIRECTORY_REQUIRED")
    external(directory.resolve(), request)
    require(directory.parent.exists(), "REKEY_EVIDENCE_PARENT_REQUIRED")
    for item in (directory.parent, *directory.parent.parents):
        require(not item.is_symlink() and not item.is_junction(), "REKEY_PATH_REDIRECTION")
    directory.mkdir()
    old, new = bytearray(), bytearray()
    pipe = bytearray()
    try:
        old = key_file(args.current_key_file, request)
        new = key_file(args.next_key_file, request)
        cmd = command(args)
        metadata = {"command": cmd, "requestSha256": hashlib.sha256(args.request.read_bytes()).hexdigest(),
                    "oldAnchorId": hashlib.sha256(old).hexdigest(), "newAnchorId": hashlib.sha256(new).hexdigest()}
        (directory / "invocation.json").write_bytes(canonical(metadata))
        pipe = old + new
        status = invoke(cmd, pipe, directory)
        (directory / "exit.json").write_bytes(canonical({"exit": status, "automaticRetry": False}))
        if status:
            return status
        verified_receipt(request, (old, new), directory)
        return 0
    finally:
        old[:] = bytes(len(old))
        new[:] = bytes(len(new))
        pipe[:] = bytes(len(pipe))


def main():
    try:
        return execute(arguments())
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"Rotation not verified ({type(error).__name__}); preserve evidence and keep writers stopped.", file=sys.stderr)
        return 3


if __name__ == "__main__":
    sys.exit(main())
