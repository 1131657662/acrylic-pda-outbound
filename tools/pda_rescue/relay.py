"""Explicit controlled relay using the operator's own Web login and durable original keys."""
import argparse
import getpass
import json
from pathlib import Path
from uuid import UUID

from .envelope import decrypt
from .integrity import archive, parse, verify_seal
from .relay_journal import RelayJournal
from .relay_transport import RelayWeb, base_url


def transfer(wire, journal, process=False):
    original = (journal.directory / "original.zip").read_bytes()
    receipt = wire.data("", original, journal.state["receiveKey"])
    journal.received(receipt)
    relay_id = str(UUID(receipt["relayId"]))
    status = wire.data("/" + relay_id + "/submission") if receipt["sourceTrust"] == "TRUSTED" else {"state": "UNVERIFIED"}
    if not process:
        return {"reception": receipt, "originalState": status["state"], "businessProcessed": False}
    if receipt["sourceTrust"] != "TRUSTED" or not receipt["submissionId"]:
        raise ValueError("Unverified or unfrozen originals cannot be submitted")
    journal.prepare_process(status["state"])
    result = wire.data("/" + relay_id + "/process", b"{}", journal.state["processKey"])
    path = journal.result(wire.relay("/results/" + str(UUID(result["resultId"]))))
    return {"resultFile": str(path), "status": result["businessReceipt"]["status"],
            "notice": "返还原机使用预置可信钥严格验签；实物放行仍按原流程核对", "localDeletionAllowed": False}


def main():
    parser = argparse.ArgumentParser(description="受控原件中转；专用仓库权限，电脑独立登录，不使用PDA Bearer")
    parser.add_argument("package", type=Path, help="完整加密.pdarescue或逐字原ZIP")
    parser.add_argument("--server", required=True, help="HTTPS服务源；隔离测试可用loopback HTTP")
    parser.add_argument("--warehouse", required=True)
    parser.add_argument("--journal", required=True, type=Path, help="受控耐久目录；丢响应时复用同目录和原包")
    parser.add_argument("--process-frozen", action="store_true", help="核原ID后通过原提交链处理可信冻结包；TEMP仍待独立审核")
    args = parser.parse_args()
    try:
        if args.package.stat().st_size > 64 * 1024 * 1024 + 128:
            raise ValueError("Original package is too large")
        plain = args.package.read_bytes()
        if plain.startswith(b"PDARESCUE1\n"):
            plain = decrypt(args.package, getpass.getpass("应急包口令："))
        if len(plain) > 32 * 1024 * 1024:
            raise ValueError("Relay supports at most 32 MiB of original ZIP bytes")
        files = archive(plain)
        verify_seal(files)
        order = parse(files["order.json"])
        server, warehouse = base_url(args.server), str(UUID(args.warehouse))
        if order["warehouseId"] != warehouse:
            raise ValueError("Original belongs to another warehouse")
        journal = RelayJournal(args.journal, plain, {"server": server, "warehouse": warehouse})
        wire = RelayWeb(server, warehouse)
        wire.login(input("电脑经办人网页登录名：").strip(), getpass.getpass("网页登录密码："))
        print(json.dumps(transfer(wire, journal, args.process_frozen), ensure_ascii=False))
    except Exception as error:
        parser.exit(1, f"中转未完成（{type(error).__name__}）；原包与已记录的原键保留，重试复用同目录。\n")


if __name__ == "__main__":
    main()
