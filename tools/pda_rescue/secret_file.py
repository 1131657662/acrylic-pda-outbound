"""Read only bounded Android package secret material; it is never an authority."""
from dataclasses import dataclass
import hashlib
import hmac
import os
from pathlib import Path
import re
import stat
import uuid

from .integrity import parse

MAX_BYTES = 4096
FORMAT = "acrylic-rescue-package-secret-v1"
PURPOSE = "只解密本次应急文件，不是登录、本人救援、主管或独立采集凭据"
CUSTODY = "敏感解密材料：与加密包及救援凭据卡分开、在设备以外保管。遗失不能恢复该包口令；有有效只读身份时可重新导出。"
BOUND = {"source", "localOrderId", "deviceId", "operatorId", "warehouseId"}
FIELDS = BOUND | {"format", "purpose", "packagePassword", "custody", "orderSha256", "materialId"}


def _identity(info):
    return info.st_dev, info.st_ino, info.st_size, info.st_mtime_ns, info.st_mode, info.st_nlink


def _regular_file(path: Path) -> Path:
    absolute = path.absolute()
    for candidate in (absolute, *absolute.parents):
        info = candidate.lstat()
        if stat.S_ISLNK(info.st_mode) or getattr(info, "st_file_attributes", 0) & 0x400:
            raise ValueError("Secret material paths cannot traverse links")
    info = absolute.stat()
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1 or not 1 <= info.st_size <= MAX_BYTES:
        raise ValueError("Secret material must be one bounded regular file")
    return absolute


def _read(path: Path) -> bytes:
    path = _regular_file(path)
    before = _identity(path.stat())
    flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    with os.fdopen(os.open(path, flags), "rb") as stream:
        if before != _identity(os.fstat(stream.fileno())):
            raise ValueError("Secret material changed before reading")
        raw = stream.read(MAX_BYTES + 1)
        after = _identity(os.fstat(stream.fileno()))
    if before != after or before != _identity(_regular_file(path).stat()) or len(raw) != before[2]:
        raise ValueError("Secret material changed while reading")
    return raw


def _schema(value) -> dict:
    if not isinstance(value, dict) or set(value) != FIELDS:
        raise ValueError("Unknown secret material fields")
    if any(not isinstance(text, str) for text in value.values()):
        raise ValueError("Secret material fields must be text")
    if (value["format"], value["purpose"], value["custody"]) != (FORMAT, PURPOSE, CUSTODY):
        raise ValueError("Secret material format mismatch")
    if not re.fullmatch(r"[A-Za-z0-9_-]{32}", value["packagePassword"]):
        raise ValueError("Invalid generated package password")
    if not re.fullmatch(r"[0-9a-f]{64}", value["orderSha256"]):
        raise ValueError("Invalid order digest")
    identifier = uuid.UUID(value["materialId"])
    if identifier.version != 4 or str(identifier) != value["materialId"]:
        raise ValueError("Invalid material identifier")
    if value["source"] not in {"FORMAL", "TEMP", "INDEPENDENT"}:
        raise ValueError("Unknown original source")
    for name in BOUND:
        text = value[name]
        if not text.strip() or len(text) > 128 or any(ord(c) < 32 or 0xD800 <= ord(c) <= 0xDFFF for c in text):
            raise ValueError("Invalid material scope")
    return value


@dataclass(frozen=True, repr=False)
class SecretMaterial:
    """Ephemeral sensitive input; repr deliberately omits its data and path."""
    path: Path
    raw: bytes
    value: dict

    @property
    def password(self) -> str:
        return self.value["packagePassword"]

    def bind(self, result: dict) -> None:
        order_bytes = result["files"]["order.json"]
        if hashlib.sha256(order_bytes).hexdigest() != self.value["orderSha256"]:
            raise ValueError("Secret material does not bind this original order")
        if any(self.value[name] != result["facts"]["order"].get(name) for name in BOUND):
            raise ValueError("Secret material scope does not match this package")
        if not hmac.compare_digest(self.raw, _read(self.path)):
            raise ValueError("Secret material changed during verification")


def load(path: Path) -> SecretMaterial:
    path = path.absolute()
    raw = _read(path)
    return SecretMaterial(path, raw, _schema(parse(raw)))
