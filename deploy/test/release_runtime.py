"""Bind renderer dependencies, Linux browser revisions and migration bytes to a release."""
import json
import zipfile
import hashlib
from release_integrity import require, safe_file

RENDERER = "report-rendering/acrylic-report-rendering"


def runtime_facts(root):
    lib = root / RENDERER / "lib"
    drivers = list(lib.glob("driver-bundle-*.jar"))
    require(len(drivers) == 1, "Missing or ambiguous Playwright driver")
    with zipfile.ZipFile(drivers[0]) as archive:
        values = json.loads(archive.read("driver/linux/package/browsers.json"))
    result = {"platform": "linux-x64"}
    for name, directory, executable in (
        ("chromium", "chromium", "chrome-linux/chrome"),
        ("chromium-headless-shell", "chromium_headless_shell", "chrome-linux/headless_shell"),
        ("ffmpeg", "ffmpeg", "ffmpeg-linux"),
    ):
        entry = next(row for row in values["browsers"] if row["name"] == name)
        require(str(entry["revision"]).isdigit(), "Invalid browser revision")
        relative = "browsers/" + directory + "-" + entry["revision"] + "/" + executable
        path = safe_file(root, relative)
        with path.open("rb") as stream:
            require(stream.read(4) == b"\x7fELF", "Browser runtime must be Linux ELF")
        result[name] = dict(revision=entry["revision"], executable=relative)
    safe_file(root, RENDERER + "/bin/acrylic-report-rendering")
    return result


def verify_binary_sources(root, source_hashes):
    with zipfile.ZipFile(root / "server.jar") as jar:
        prefix = "server/src/main/resources/db/migration/"
        expected_files = {"BOOT-INF/classes/db/migration/" + name.removeprefix(prefix)
                          for name in source_hashes if name.startswith(prefix)}
        jar_files = {name for name in jar.namelist() if name.startswith("BOOT-INF/classes/db/migration/")
                     and not name.endswith("/")}
        require(jar_files == expected_files, "JAR migration inventory mismatch")
        for name, expected in source_hashes.items():
            prefix = "server/src/main/resources/db/migration/"
            if name.startswith(prefix):
                binary = jar.read("BOOT-INF/classes/db/migration/" + name.removeprefix(prefix))
                require(hashlib.sha256(binary).hexdigest() == expected, "JAR migration/source mismatch")
        renderers = list((root / RENDERER / "lib").glob("acrylic-report-rendering*.jar"))
        require(len(renderers) == 1, "Missing or ambiguous renderer JAR")
        binary = jar.read("BOOT-INF/lib/" + renderers[0].name)
        require(hashlib.sha256(binary).digest() == hashlib.sha256(renderers[0].read_bytes()).digest(),
                "Server/renderer binary mismatch")
