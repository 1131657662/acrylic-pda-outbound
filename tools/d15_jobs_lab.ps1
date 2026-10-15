#requires -Version 5.1
<# Fresh synthetic monitor instance only. Does not reset, reuse, or stop existing instances. #>
[CmdletBinding()]
param([ValidateRange(1543,9999)][int]$Run=1543,[ValidateRange(1024,65535)][int]$Port=19343)
$ErrorActionPreference='Stop'
$root='D:/acrylic-pda-outbound'
$attempt="$root/.local/d15-monitor-$Run"
$mysqlBin='C:/Program Files/MySQL/MySQL Server 8.0/bin'
if(Test-Path -LiteralPath $attempt){throw 'Existing attempt retained; allocate another run'}
if(@(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object {$_.LocalPort -eq $Port}).Count){throw 'Port occupied'}
if((Get-PSDrive D).Free -lt 3GB){throw 'Insufficient growth reserve'}
New-Item -ItemType Directory -Path $attempt,"$attempt/private","$attempt/tmp" | Out-Null
$sid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls "$attempt/private" '/inheritance:r' '/grant:r' "*${sid}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
if($LASTEXITCODE){throw 'Private ACL failed'}
$utf8=[Text.UTF8Encoding]::new($false)
$password=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
$schema="d15_monitor_$Run"
[IO.File]::WriteAllText("$attempt/private/admin.cnf","[client]`nuser=d15_admin`npassword=$password`n",$utf8)
$bootstrap="CREATE DATABASE $schema;`nCREATE USER 'd15_admin'@'127.0.0.1' IDENTIFIED BY '$password';`nGRANT ALL ON *.* TO 'd15_admin'@'127.0.0.1' WITH GRANT OPTION;`n"
[IO.File]::WriteAllText("$attempt/private/bootstrap.sql",$bootstrap,$utf8)
$env:TEMP="$attempt/tmp"; $env:TMP=$env:TEMP
& "$mysqlBin/mysqld.exe" '--no-defaults' '--initialize-insecure' "--datadir=$attempt/data" '--mysqlx=OFF' "--log-error=$attempt/initialize.log" *> "$attempt/initialize-stdout.log"
if($LASTEXITCODE){throw 'Initialization failed; preserve attempt'}
$args=@('--no-defaults',"--datadir=$attempt/data","--port=$Port",'--bind-address=127.0.0.1','--mysqlx=OFF','--skip-name-resolve',
 '--innodb-buffer-pool-size=64M','--log-bin-trust-function-creators=ON',"--log-bin=$attempt/binlog","--log-error=$attempt/server.log","--init-file=$attempt/private/bootstrap.sql")
$proc=Start-Process -FilePath "$mysqlBin/mysqld.exe" -ArgumentList $args -WindowStyle Hidden -PassThru -RedirectStandardOutput "$attempt/server-stdout.log" -RedirectStandardError "$attempt/server-stderr.log"
$manifest=@{run=$Run;port=$Port;root=$attempt;datadir="$attempt/data";database=$schema;process_id=$proc.Id;purpose='D15_SYNTHETIC_MONITOR_ONLY';input_head=(git -C $root rev-parse HEAD)}
$manifest | ConvertTo-Json | Set-Content "$attempt/manifest.json"
Write-Output "Fresh monitor instance started pid=$($proc.Id); readiness and identity must be checked by the fixture."
