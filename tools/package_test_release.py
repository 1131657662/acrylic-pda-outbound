"""Build an immutable backend archive from one clean committed source tree."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
sys.dont_write_bytecode = True
import tarfile
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "deploy/test"))
from release_facts import facts, source_inputs
from release_integrity import require, safe_file, sha256
from release_runtime import RENDERER, runtime_facts
from verify_backend_release import verify


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], encoding="utf-8").strip()


def source_record(root):
    require(not git(root, "status", "--porcelain", "-uall"), "Commit source changes before packaging")
    paths = git(root, "ls-files", "-z").split("\0")
    hashes = {name: sha256(safe_file(root, name)) for name in paths if name}
    return dict(gitHead=git(root, "rev-parse", "HEAD"), sourceHashes=hashes)


def copy_runtime(source, target):
    require(source.is_dir() and not source.is_symlink(), "Runtime input must be a real directory")
    require(not any(p.is_symlink() for p in source.rglob("*")), "Runtime links are not supported")
    shutil.copytree(source, target)


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def copy_browsers(options, destination):
    drivers = list((options.renderer / "lib").glob("driver-bundle-*.jar"))
    require(len(drivers) == 1, "Missing or ambiguous Playwright driver")
    with zipfile.ZipFile(drivers[0]) as jar:
        values = json.loads(jar.read("driver/linux/package/browsers.json"))
    target = destination / "browsers"
    target.mkdir()
    for name in ("chromium", "chromium-headless-shell", "ffmpeg"):
        row = next(row for row in values["browsers"] if row["name"] == name)
        directory = name.replace("-", "_") + "-" + row["revision"]
        copy_runtime(options.browsers / directory, target / directory)


def assemble(options, record):
    root, destination = options.root, options.output / options.release
    require(not destination.exists(), "Immutable release directory already exists")
    destination.mkdir(parents=True)
    jar = root / "server/build/libs/acrylic-outbound-server-0.1.0.jar"
    shutil.copy2(jar, destination / "server.jar")
    copy_runtime(options.renderer, destination / RENDERER)
    copy_browsers(options, destination)
    deploy = destination / "deploy"
    deploy.mkdir()
    for source in (root / "deploy/test").iterdir():
        if source.is_file():
            target = deploy / source.name
            target.write_bytes(source.read_bytes())
    for name in source_inputs(root):
        target = destination / "source" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(safe_file(root, name).read_bytes())
    write_json(destination / "source-sha256.json", record)
    return destination


def manifest(options, destination, record):
    hashes = {p.relative_to(destination).as_posix(): sha256(p)
              for p in destination.rglob("*") if p.is_file()}
    result = dict(formatVersion=1, release=options.release,
                  createdAt=datetime.now(timezone.utc).isoformat(),
                  environment="independent-test", gitStatus=[], **record,
                  **facts(options.root), serverSha256=hashes["server.jar"],
                  artifactHashes=hashes, runtime=runtime_facts(destination))
    write_json(destination / "manifest.json", result)


def archive(destination):
    output = destination.with_name(destination.name + ".tar.gz")
    require(not output.exists(), "Immutable archive already exists")
    with tarfile.open(output, "x:gz", format=tarfile.PAX_FORMAT) as tar:
        for path in sorted(destination.rglob("*")):
            info = tar.gettarinfo(str(path), path.relative_to(destination).as_posix())
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            info.mode = 0o755 if path.is_dir() or path.suffix == ".sh" or "/bin/" in str(path).replace("\\", "/") else 0o644
            if path.is_file():
                with path.open("rb") as stream:
                    if stream.read(4) == b"\x7fELF":
                        info.mode = 0o755
                    stream.seek(0)
                    tar.addfile(info, stream)
            else:
                tar.addfile(info)
    digest = sha256(output)
    output.with_name(output.name + ".sha256").write_text(digest + "  " + output.name + "\n", encoding="ascii")
    return dict(archive=str(output), sha256=digest, bytes=output.stat().st_size)


def verify_build(options, record):
    receipt = json.loads(options.build_record.read_text(encoding="utf-8"))
    require(receipt["gitHead"] == record["gitHead"] and receipt["sourceHashes"] == record["sourceHashes"],
            "Build inputs do not match candidate source")
    require(receipt["exitCode"] == 0, "Build did not succeed")
    artifacts = {"server.jar": options.root / "server/build/libs/acrylic-outbound-server-0.1.0.jar"}
    artifacts.update({RENDERER + "/" + p.relative_to(options.renderer).as_posix(): p
                      for p in options.renderer.rglob("*") if p.is_file()})
    require(receipt["artifactHashes"] == {name: sha256(path) for name, path in artifacts.items()},
            "Build artifacts changed or are stale")


def package(options):
    require(re.fullmatch(r"[A-Za-z0-9._-]+", options.release), "Unsafe release name")
    record = source_record(options.root)
    verify_build(options, record)
    destination = assemble(options, record)
    shutil.copyfile(options.build_record, destination / "build-receipt.json")
    require(source_record(options.root) == record, "Source changed while packaging")
    manifest(options, destination, record)
    verify(destination)
    result = dict(gitHead=record["gitHead"], manifestSha256=sha256(destination / "manifest.json"),
                  sourceManifestSha256=sha256(destination / "source-sha256.json"), **archive(destination))
    write_json(options.output / (options.release + ".receipt.json"), result)
    return result


def arguments():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("root", "renderer", "browsers", "output", "build-record"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--release", required=True)
    return parser.parse_args()


if __name__ == "__main__":
    print(json.dumps(package(arguments()), sort_keys=True))
