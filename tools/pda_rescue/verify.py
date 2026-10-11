"""Read-only package verification entry point."""
from .integrity import archive, source_trust, verify_seal
from .facts import verify_facts


def verify(plain: bytes, trusted: dict | None = None) -> dict:
    files = archive(plain)
    manifest = verify_seal(files)
    facts = verify_facts(files, manifest)
    return {"files": files, "manifest": manifest, "facts": facts,
            "sourceTrusted": source_trust(files, facts["order"], trusted),
            "businessReceiptVerified": False}
