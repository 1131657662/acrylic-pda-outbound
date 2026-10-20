"""Synthetic package fixture for command-boundary tests; never release evidence."""
import json
from pathlib import Path
import shutil
import zipfile
from release_facts import facts, source_inputs
from release_integrity import sha256
from release_runtime import RENDERER, runtime_facts
from verify_backend_release import REQUIRED

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = facts(ROOT)["flyway"]


def write_json(path, value):
    path.write_text(json.dumps(value), encoding="utf-8")


def renderer_fixture(release):
    lib = release / RENDERER / "lib"
    lib.mkdir(parents=True)
    with zipfile.ZipFile(lib / "acrylic-report-rendering.jar", "w") as jar:
        jar.writestr("fixture", "not a runtime")
    rows = []
    for name, directory, executable in (
        ("chromium", "chromium", "chrome-linux/chrome"),
        ("chromium-headless-shell", "chromium_headless_shell", "chrome-linux/headless_shell"),
        ("ffmpeg", "ffmpeg", "ffmpeg-linux"),
    ):
        rows.append(dict(name=name, revision="1"))
        target = release / "browsers" / (directory + "-1") / executable
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(b"\x7fELFfixture: never executable")
    with zipfile.ZipFile(lib / "driver-bundle-fixture.jar", "w") as jar:
        jar.writestr("driver/linux/package/browsers.json", json.dumps(dict(browsers=rows)))
    launcher = release / RENDERER / "bin/acrylic-report-rendering"
    launcher.parent.mkdir()
    launcher.write_text("fixture", encoding="utf-8")


def source_fixture(release):
    hashes = {}
    for name in source_inputs(ROOT):
        target = release / "source" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT / name, target)
        hashes[name] = sha256(target)
    return dict(gitHead="a" * 40, sourceHashes=hashes)


def jar_fixture(release, record):
    with zipfile.ZipFile(release / "server.jar", "w") as jar:
        for name in record["sourceHashes"]:
            prefix = "server/src/main/resources/db/migration/"
            if name.startswith(prefix):
                jar.write(ROOT / name, "BOOT-INF/classes/db/migration/" + name.removeprefix(prefix))
        renderer = release / RENDERER / "lib/acrylic-report-rendering.jar"
        jar.write(renderer, "BOOT-INF/lib/" + renderer.name)


def make_package(release, base):
    for relative in REQUIRED - {"server.jar", "source-sha256.json", "build-receipt.json"}:
        target = release / relative
        target.parent.mkdir(exist_ok=True)
        shutil.copyfile(Path(__file__).parent / target.name, target)
    script = release / "deploy/activate_backend.sh"
    value = script.read_text().replace("/opt/acrylic-outbound-test", str(base))
    value = value.replace("/run/lock/acrylic-outbound-test-backup.lock", str(base / "backup.lock"))
    script.write_text(value)
    record = source_fixture(release)
    renderer_fixture(release)
    jar_fixture(release, record)
    write_json(release / "source-sha256.json", record)
    build_hashes = {p.relative_to(release).as_posix(): sha256(p) for p in release.rglob("*")
                    if p.is_file() and (p.name == "server.jar" or RENDERER in p.as_posix())}
    write_json(release / "build-receipt.json", dict(**record, exitCode=0, artifactHashes=build_hashes))
    hashes = {p.relative_to(release).as_posix(): sha256(p) for p in release.rglob("*") if p.is_file()}
    value = dict(formatVersion=1, release=release.name, environment="independent-test", gitStatus=[],
                 **record, **facts(ROOT), artifactHashes=hashes, serverSha256=hashes["server.jar"],
                 runtime=runtime_facts(release))
    write_json(release / "manifest.json", value)
