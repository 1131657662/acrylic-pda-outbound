"""Validate the V32 global Beijing-day counter without allocating or repairing it."""
from datetime import datetime
import re
from safety import require


def order_highwaters(rows):
    highs, count, legacy = {}, 0, 0
    for number, created_date in rows:
        if not number.startswith('FH'):
            legacy += 1
            continue
        match = re.fullmatch(r'FH([0-9]{8})([0-9]{4,10})', number)
        require(match is not None, 'ORDER_NUMBER_INVALID')
        day, sequence = match.groups()
        require(day == created_date, 'ORDER_NUMBER_DATE_MISMATCH')
        datetime.strptime(day, '%Y%m%d')
        value = int(sequence)
        require(0 < value <= 2147483647 and sequence == f'{value:04d}', 'ORDER_NUMBER_INVALID')
        highs[day] = max(highs.get(day, 0), value)
        count += 1
    return highs, count, legacy


def compare_counters(highs, rows):
    counters = dict(rows)
    require(len(counters) == len(rows), 'ORDER_COUNTER_DUPLICATE_DATE')
    require(all(0 <= value <= 2147483647 for value in counters.values()), 'ORDER_COUNTER_INVALID')
    for day, high in highs.items():
        require(day in counters, 'ORDER_COUNTER_MISSING')
        require(counters[day] >= high, 'ORDER_COUNTER_BEHIND')
    return {'order_counter_dates_verified': len(counters),
            'order_counter_exhausted_dates': sum(value == 2147483647 for value in counters.values())}


def verify_counters(db):
    rows = [line.split('\t') for line in db.query("SELECT order_no,DATE_FORMAT(created_at + INTERVAL 8 HOUR,'%Y%m%d') "
                                                 "FROM outbound_order ORDER BY id;").splitlines()]
    highs, count, legacy = order_highwaters(rows)
    rows = [line.split('\t') for line in db.query("SELECT DATE_FORMAT(business_date,'%Y%m%d'),last_sequence "
                                                 "FROM order_number_counter ORDER BY business_date;").splitlines()]
    result = compare_counters(highs, [(day, int(value)) for day, value in rows])
    return {**result, 'fh_orders_verified': count, 'legacy_orders_preserved': legacy}
