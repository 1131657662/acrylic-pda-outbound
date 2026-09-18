#!/usr/bin/env python3
"""Initialize a new isolated test environment; refuse existing state."""
import os
from pathlib import Path
import pwd
import secrets
import shlex
import subprocess

CONFIG = Path('/etc/acrylic-outbound-test')
DATA = Path('/var/lib/acrylic-outbound-test')
ACCOUNT = 'acrylic-outbound-test'
DATABASE = 'acrylic_outbound_test'
RUNTIME = 'acrylic_test_runtime'
MIGRATION = 'acrylic_test_migrate'


def mysql(sql):
    result = subprocess.run(
        ['mysql', '--defaults-file=/etc/mysql/debian.cnf', '--protocol=socket', '-NB'],
        input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('MySQL setup failed; inspect server securely before retrying')
    return result.stdout.strip()


def preflight():
    if os.geteuid() != 0 or CONFIG.exists() or DATA.exists():
        raise RuntimeError('Requires root and an unused test environment')
    state = mysql("SELECT COUNT(*) FROM information_schema.schemata "
                  "WHERE schema_name='acrylic_outbound_test'; "
                  "SELECT COUNT(*) FROM mysql.user "
                  "WHERE user IN ('acrylic_test_runtime','acrylic_test_migrate');")
    if state.splitlines() != ['0', '0']:
        raise RuntimeError('Database or accounts already exist; refusing replacement')


def write_environment(name, values):
    content = ''.join(f'{key}={shlex.quote(value)}\n' for key, value in values.items())
    (CONFIG / name).write_text(content)


def create_directories():
    subprocess.run(['useradd', '--system', '--home-dir', str(DATA),
                    '--shell', '/usr/sbin/nologin', ACCOUNT], check=True)
    identity = pwd.getpwnam(ACCOUNT)
    CONFIG.mkdir(mode=0o750)
    CONFIG.chmod(0o750)
    os.chown(CONFIG, 0, identity.pw_gid)
    DATA.mkdir(mode=0o755)
    DATA.chmod(0o755)
    for name in ('files', 'acme'):
        path = DATA / name
        path.mkdir(mode=0o700 if name == 'files' else 0o755)
        path.chmod(0o700 if name == 'files' else 0o755)
        if name == 'files':
            os.chown(path, identity.pw_uid, identity.pw_gid)
    for path in (Path('/opt') / ACCOUNT, Path('/opt') / ACCOUNT / 'releases'):
        path.mkdir(mode=0o755)
        path.chmod(0o755)


def create_signing_key():
    private = CONFIG / 'signing.pk8'
    public = CONFIG / 'signing.der'
    generated = subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA',
                                '-pkeyopt', 'rsa_keygen_bits:3072'],
                               capture_output=True, check=True).stdout
    subprocess.run(['openssl', 'pkcs8', '-topk8', '-nocrypt', '-outform', 'DER',
                    '-out', str(private)], input=generated, check=True)
    subprocess.run(['openssl', 'pkey', '-pubout', '-outform', 'DER',
                    '-out', str(public)], input=generated, check=True)
    for path in (private, public):
        path.chmod(0o640)
        os.chown(path, 0, pwd.getpwnam(ACCOUNT).pw_gid)


def create_database(passwords):
    # Identifiers are constants; generated passwords contain hexadecimal digits only.
    mysql(f"CREATE DATABASE {DATABASE} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
          f"CREATE USER '{RUNTIME}'@'localhost' IDENTIFIED BY '{passwords[RUNTIME]}';"
          f"CREATE USER '{MIGRATION}'@'localhost' IDENTIFIED BY '{passwords[MIGRATION]}';"
          f"GRANT SELECT,INSERT,UPDATE,DELETE ON {DATABASE}.* TO '{RUNTIME}'@'localhost';"
          f"GRANT ALL PRIVILEGES ON {DATABASE}.* TO '{MIGRATION}'@'localhost';")


def create_configuration(passwords):
    write_environment('runtime.env', {
        'OUTBOUND_DB_URL': f'jdbc:mysql://127.0.0.1:3306/{DATABASE}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true',
        'OUTBOUND_DB_USER': RUNTIME, 'OUTBOUND_DB_PASSWORD': passwords[RUNTIME],
        'OUTBOUND_MIGRATE': 'false', 'OUTBOUND_COOKIE_SECURE': 'true',
        'OUTBOUND_BIND': '127.0.0.1', 'OUTBOUND_PORT': '18085',
        'OUTBOUND_STORAGE': str(DATA / 'files'),
        'OUTBOUND_AUTH_SIGNING_PRIVATE_KEY': str(CONFIG / 'signing.pk8'),
        'OUTBOUND_AUTH_SIGNING_PUBLIC_KEY': str(CONFIG / 'signing.der'),
        'SERVER_FORWARD_HEADERS_STRATEGY': 'native',
        'SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES': r'127\.0\.0\.1',
    })
    write_environment('migration.env', {
        'OUTBOUND_DB_USER': MIGRATION, 'OUTBOUND_DB_PASSWORD': passwords[MIGRATION]})
    write_environment('bootstrap.env', {
        'OUTBOUND_BOOTSTRAP_USERNAME': 'test_admin',
        'OUTBOUND_BOOTSTRAP_PASSWORD': secrets.token_hex(16)})
    (CONFIG / 'migration.cnf').write_text(
        f'[client]\nuser={MIGRATION}\npassword={passwords[MIGRATION]}\nprotocol=socket\n')


def main():
    os.umask(0o077)
    preflight()
    create_directories()
    passwords = {name: secrets.token_hex(24) for name in (RUNTIME, MIGRATION)}
    create_configuration(passwords)
    create_signing_key()
    create_database(passwords)
    print('Initialized independent test configuration, signing key and database accounts.')


if __name__ == '__main__':
    main()
