"""Read only an explicit, private MySQL monitor credential file."""
import configparser
import os
from pathlib import Path
import stat
import subprocess
from safety import regular, require


def private_file(path):
    path = regular(path)
    details = path.stat()
    require(details.st_nlink == 1 and details.st_size <= 4096, 'JOB_CREDENTIAL_FILE_INVALID')
    if os.name != 'nt':
        require(stat.S_IMODE(details.st_mode) & 0o077 == 0, 'JOB_CREDENTIAL_FILE_NOT_PRIVATE')
        require(details.st_uid == os.getuid(), 'JOB_CREDENTIAL_OWNER_INVALID')
    else:
        script = '''$ErrorActionPreference='Stop'
$p=[Console]::In.ReadLine(); $a=Get-Acl -LiteralPath $p -ErrorAction Stop
$allowed=@([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')
$bad=@($a.Access | Where-Object { $_.AccessControlType -eq 'Allow' -and
  $_.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value -notin $allowed })
if($bad.Count -eq 0){'PRIVATE'}else{'REFUSED'}'''
        env = {key: value for key, value in os.environ.items() if key.casefold() != 'psmodulepath'}
        result = subprocess.run(['powershell', '-NoProfile', '-NonInteractive', '-Command', script],
                                input=str(path.resolve()) + '\n', capture_output=True, text=True, timeout=5, env=env)
        require(result.returncode == 0 and not result.stderr and result.stdout.strip() == 'PRIVATE',
                'JOB_CREDENTIAL_FILE_NOT_PRIVATE')
    return path


def credentials(path):
    config = configparser.ConfigParser(interpolation=None)
    config.read_string(private_file(Path(path)).read_text(encoding='utf-8'))
    require(not config.defaults() and config.sections() == ['client'], 'JOB_CREDENTIAL_FORMAT_INVALID')
    require(set(config['client']) == {'user', 'password'}, 'JOB_CREDENTIAL_FORMAT_INVALID')
    require(bool(config['client']['user']) and bool(config['client']['password']), 'JOB_CREDENTIAL_EMPTY')
    return dict(config['client'])
