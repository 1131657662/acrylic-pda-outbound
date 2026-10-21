"""Decrypt and verify locally. No network client, database writer, or submission endpoint."""
import argparse
import getpass
import json
from pathlib import Path
import tempfile

from .envelope import decrypt
from .integrity import parse
from .secret_file import load as load_secret
from .verify import verify


class RescuePublishError(RuntimeError):
    """A partial directory is retained and is never advertised as complete."""


def _write_contents(result: dict, directory: Path):
    for name, data in result["files"].items():
        (directory / name).write_bytes(data)
    report = {"sourceTrusted": result["sourceTrusted"], "businessReceiptVerified": False,
              "notice": "只读核对资料，不等于出库确认或放行；电脑修改不能代替原件。",
              "manifest": result["manifest"], "facts": result["facts"]}
    (directory / "verified.json").write_text(json.dumps(report, ensure_ascii=False), encoding="utf-8")


def publish(result: dict, destination: Path, converter=None):
    if destination.exists():
        raise ValueError("Output already exists; original files will not be overwritten")
    destination.parent.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix="pda-rescue-partial-", dir=destination.parent))
    try:
        _write_contents(result, directory)
        if converter:
            converter(directory)
        directory.rename(destination)
    except Exception as error:
        raise RescuePublishError(f"未发布完整目录；失败资料保留在 {directory}") from error


def arguments():
    parser = argparse.ArgumentParser(description="PDA只读应急包离线核验；不执行补传、记账或放行")
    parser.add_argument("package", type=Path)
    parser.add_argument("--trust", type=Path, help="包外预先核验的登记设备/人员/仓库公钥清单")
    parser.add_argument("--secret-file", type=Path, help="Android生成的独立解密材料文件；绑定包内原订单")
    parser.add_argument("--require-trusted", action="store_true")
    parser.add_argument("--out", type=Path, help="新建已核验原件目录（包含解密后业务资料，请受控保管）")
    parser.add_argument("--xlsx", action="store_true", help="核验后在输出目录生成Excel；需要既有Artifact Tool运行库")
    return parser, parser.parse_args()


def main():
    parser, args = arguments()
    try:
        trusted = parse(args.trust.read_bytes()) if args.trust else None
        material = load_secret(args.secret_file) if args.secret_file else None
        password = material.password if material else getpass.getpass("应急包口令（与介质分开保管）：")
        result = verify(decrypt(args.package, password), trusted)
        if material:
            material.bind(result)
        if args.require_trusted and not result["sourceTrusted"]:
            raise ValueError("缺少包外可信登记公钥；仅完整性核验不能证明来源")
        if args.xlsx and not args.out:
            raise ValueError("Excel转换必须指定新输出目录")
        if args.out:
            from .conversion import convert
            publish(result, args.out, convert if args.xlsx else None)
        print(json.dumps({"integrityVerified": True, "sourceTrusted": result["sourceTrusted"],
                          "businessReceiptVerified": False, "summary": result["manifest"]["summary"]}, ensure_ascii=False))
    except RescuePublishError as error:
        parser.exit(1, f"{error}\n")
    except Exception as error:
        parser.exit(1, f"核验/写入未完成（{type(error).__name__}）；原包保留，不据此改变业务状态。\n")


if __name__ == "__main__":
    main()
