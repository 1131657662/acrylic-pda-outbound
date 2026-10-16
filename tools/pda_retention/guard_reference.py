"""Validate guarded chains retained outside the selected whole-order facts."""
from .codec import require


def unrelated_guards(rows):
    scans = {r['captureId']: r for r in rows['work_scan']}
    captures = {r['id']: r for r in rows['work_capture']}
    orders = {r['assignmentId']: r for r in rows['work_order']}
    for guard in rows['work_guard']:
        scan, capture = scans.get(guard['captureId']), captures.get(guard['captureId'])
        require(scan is not None and capture is not None, 'Unresolved guard capture reference')
        order = orders.get(guard['assignmentId'])
        require(order is not None, 'Unresolved guard order reference')
        require(scan['assignmentId'] == capture['assignmentId'] == guard['assignmentId'],
                'Cross-assignment guard reference')
        require(scan['barcode'] == capture['barcode'] == guard['barcode'], 'Guard barcode mismatch')
        require(capture['orderId'] == order['orderId'], 'Guard order identity mismatch')
        require(capture['originAssignmentId'] in ('', guard['assignmentId']),
                'Cross-origin guard reference protected')
        require(scan['status'] == 'ACTIVE' and capture['state'] == 'COMMITTED',
                'Unresolved or disposed guard history protected')
        require(scan['expectedDispatchCycle'] == capture['expectedDispatchCycle'] == guard['dispatchCycle']
                and guard['dispatchCycle'] > 0, 'Guard dispatch cycle mismatch')
