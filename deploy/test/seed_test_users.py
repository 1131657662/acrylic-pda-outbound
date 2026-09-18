#!/usr/bin/env python3
"""Create two PDA test operators in the dedicated R02 database only."""
import json
import os
import secrets
import uuid
import bcrypt
from initialize import CONFIG, mysql


def create_operator(username, password):
    identifier = uuid.uuid4().hex
    digest = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=12)).decode()
    return f"""
INSERT INTO app_user(id,username,display_name,password_hash,created_at)
VALUES(UNHEX('{identifier}'),'{username}','{username}','{digest}',UTC_TIMESTAMP(3));
INSERT INTO user_role SELECT UNHEX('{identifier}'),id FROM role WHERE code='PDA_OPERATOR';
INSERT INTO user_warehouse SELECT UNHEX('{identifier}'),id FROM warehouse WHERE code='MAIN';
INSERT INTO operation_event(actor_id,event_type,entity_type,entity_id,details,server_time,request_id)
SELECT id,'TEST_OPERATOR_CREATED','USER',UNHEX('{identifier}'),'{{"purpose":"R02 isolated test"}}',UTC_TIMESTAMP(3),'r02-test-bootstrap'
FROM app_user WHERE username='test_admin';
"""


def main():
    os.umask(0o077)
    credentials = CONFIG / 'test-users.json'
    if os.geteuid() != 0 or credentials.exists():
        raise RuntimeError('Requires root and absent test-user credentials; refusing replacement')
    state = mysql("USE acrylic_outbound_test; SELECT COUNT(*) FROM app_user "
                  "WHERE username IN ('test_pda01','test_pda02'); "
                  "SELECT COUNT(*) FROM app_user WHERE username='test_admin';")
    if state.splitlines() != ['0', '1']:
        raise RuntimeError('Unexpected identity state; refusing changes')
    users = {name: secrets.token_hex(12) for name in ('test_pda01', 'test_pda02')}
    credentials.write_text(json.dumps(users))
    statements = ''.join(create_operator(name, password) for name, password in users.items())
    mysql('USE acrylic_outbound_test; START TRANSACTION;' + statements + 'COMMIT;')
    print('Created two isolated PDA operators with warehouse scope and audit records.')


if __name__ == '__main__':
    main()
