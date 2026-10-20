"""Derive compatibility from canonical sources, without release version pins."""
import re

FACT_INPUTS = (
    "contracts/openapi.yaml", "android/app/build.gradle.kts",
    "android/app/src/main/java/com/acrylic/outbound/pda/data/PdaDatabase.kt",
)


def matched(text, pattern):
    value = re.search(pattern, text, re.MULTILINE)
    if not value:
        raise ValueError("Cannot derive release compatibility")
    return value.group(1)


def facts(root):
    contract, gradle, database = [(root / p).read_text(encoding="utf-8") for p in FACT_INPUTS]
    migrations = sorted((root / "server/src/main/resources/db/migration").glob("V*__*.sql"))
    numbers = [int(matched(p.name, r"^V(\d+)__")) for p in migrations]
    if not numbers or sorted(numbers) != list(range(1, max(numbers) + 1)):
        raise ValueError("Migration sequence is incomplete or duplicated")
    room = re.search(r"version\s*=\s*(\d+)", database)
    return dict(contract=matched(contract, r"^  version:\s*(\S+)\s*$"),
                flyway=max(numbers), migrations=len(numbers),
                pda=matched(gradle, r'pdaVersionName"\).orElse\("([^"]+)"'),
                pdaCode=int(matched(gradle, r'pdaVersionCode"\).map\(String::toInt\).orElse\((\d+)\)')),
                room=int(room.group(1) if room else matched(database, r"PDA_DATABASE_VERSION\s*=\s*(\d+)")))


def source_inputs(root):
    return list(FACT_INPUTS) + [p.relative_to(root).as_posix() for p in sorted(
        (root / "server/src/main/resources/db/migration").glob("V*__*.sql"))]
