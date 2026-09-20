"""Compare PDA_TEMP target revisions and lines with the retained original plan."""
from uuid import UUID
from archive_proofs import parse
from safety import require


def verify_plan(db, row, body):
    order = UUID(row['order']).hex
    plan, manifest = body['temporaryOrder'], parse(row['manifest'])
    rows = db.query("SELECT JSON_OBJECT('id',BIN_TO_UUID(r.id),'revision',r.revision,'current',o.current_revision,"
        "'snapshot',BIN_TO_UUID(r.inventory_snapshot_id),'mapping',r.mapping_revision,'vehicle',r.vehicle_no,"
        "'container',r.container_no,'reason',r.reason,'planned',r.planned_total,'actor',BIN_TO_UUID(r.created_by),"
        "'warehouse',BIN_TO_UUID(r.warehouse_id)) FROM order_revision r INNER JOIN outbound_order o ON o.id=r.order_id "
        f"WHERE r.order_id=UNHEX('{order}');").splitlines()
    require(len(rows) == 1, 'TEMP_TARGET_REVISION_MISMATCH')
    actual = parse(rows[0])
    expected = dict(revision=int(plan['revision']), current=int(plan['revision']), snapshot=manifest['inventorySnapshotId'],
        mapping=int(manifest['mappingRevision']), vehicle=plan['vehicleNo'], container=plan['containerNo'], reason=plan['reason'],
        planned=sum(int(line['plannedCount']) for line in plan['lines']), actor=row['actor'], warehouse=row['warehouse'])
    require(all(actual.get(k) == v for k, v in expected.items()), 'TEMP_TARGET_PLAN_MISMATCH')
    revision = UUID(actual['id']).hex
    lines = [parse(line) for line in db.query("SELECT JSON_OBJECT('deliveryCode',delivery_code,'productId',BIN_TO_UUID(product_id),"
        "'model',model_snapshot,'name',name_snapshot,'plannedCount',CAST(planned_count AS CHAR)) FROM order_line "
        f"WHERE revision_id=UNHEX('{revision}') ORDER BY delivery_code;").splitlines()]
    require(lines == plan['lines'], 'TEMP_TARGET_LINES_MISMATCH')
