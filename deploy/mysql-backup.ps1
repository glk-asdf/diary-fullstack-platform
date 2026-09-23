<#
.SYNOPSIS
    MySQL 逻辑备份：导出 diary 库到 deploy/backups，并清理过期文件。

.DESCRIPTION
    在运行中的 MySQL 容器内执行 mysqldump，因此宿主机不需要安装 mysql 客户端。
    先用 --result-file 写到容器内 /tmp（保证字节精确、不受 PowerShell 编码/BOM 影响），
    再用 docker compose cp 拷到宿主机。

.USAGE
    powershell -ExecutionPolicy Bypass -File deploy\mysql-backup.ps1

.计划任务（每天 02:30 自动执行）
    schtasks /Create /TN "diary-mysql-backup" /SC DAILY /ST 02:30 ^
      /TR "powershell -NoProfile -ExecutionPolicy Bypass -File <本脚本的绝对路径>" /F
#>
$ErrorActionPreference = 'Stop'

$repoRoot   = Split-Path -Parent $PSScriptRoot
$backupDir  = Join-Path $PSScriptRoot 'backups'
$retainDays = 7
$service    = 'mysql'

function Read-DotEnv([string] $path) {
    $map = @{}
    if (-not (Test-Path $path)) { return $map }
    foreach ($raw in Get-Content $path) {
        $line = $raw.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        $eq = $line.IndexOf('=')
        if ($eq -lt 1) { continue }
        $map[$line.Substring(0, $eq).Trim()] = $line.Substring($eq + 1).Trim()
    }
    return $map
}

if (-not (Test-Path $backupDir)) {
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
}

$envMap      = Read-DotEnv (Join-Path $repoRoot '.env')
$dbName      = if ($envMap['DB_NAME']) { $envMap['DB_NAME'] } else { 'diary' }
$dbPassword  = $envMap['DB_PASSWORD']
if (-not $dbPassword) { throw "未在 $repoRoot\.env 中找到 DB_PASSWORD" }

Push-Location $repoRoot
try {
    $stamp        = Get-Date -Format 'yyyyMMdd-HHmmss'
    $target       = Join-Path $backupDir "diary-$stamp.sql"
    $containerOut = "/tmp/diary-$stamp.sql"

    Write-Host "开始备份 $dbName ..."

    # 密码经 MYSQL_PWD 传入，不出现在容器内的进程命令行里
    $dumpArgs = "mysqldump -uroot --single-transaction --routines --triggers " +
                "--databases $dbName --result-file=$containerOut"
    docker compose exec -T -e "MYSQL_PWD=$dbPassword" $service sh -c $dumpArgs
    if ($LASTEXITCODE -ne 0) { throw "mysqldump 执行失败（退出码 $LASTEXITCODE）" }

    # compose cp 会把进度写到 stderr，PowerShell 将其包装成 NativeCommandError；
    # 在本脚本 $ErrorActionPreference='Stop' 的设定下会被当成失败并打印一大段红字。
    # 这里局部放宽并显式丢弃，成功与否改由 $LASTEXITCODE 判断。
    $previousEap = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    docker compose cp "${service}:$containerOut" $target 2>&1 | Out-Null
    $ErrorActionPreference = $previousEap
    if ($LASTEXITCODE -ne 0) { throw "备份文件拷出失败" }

    # 容器内临时文件及时清理，避免堆积在可写层
    docker compose exec -T $service rm -f $containerOut | Out-Null

    $sizeKb = [math]::Round((Get-Item $target).Length / 1KB, 1)
    Write-Host "备份完成: $target ($sizeKb KB)"

    # 清理过期备份
    $expired = Get-ChildItem $backupDir -Filter 'diary-*.sql' -ErrorAction SilentlyContinue |
               Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$retainDays) }
    foreach ($file in $expired) {
        Remove-Item $file.FullName -Force -ErrorAction SilentlyContinue
        Write-Host "已清理过期备份: $($file.Name)"
    }

    $kept = (Get-ChildItem $backupDir -Filter 'diary-*.sql' -ErrorAction SilentlyContinue).Count
    Write-Host "当前保留 $kept 份备份（保留 $retainDays 天）"
}
finally {
    Pop-Location
}
