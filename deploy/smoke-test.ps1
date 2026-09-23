<#
.SYNOPSIS
    部署后冒烟测试：覆盖 登录 / CRUD / 上传 / 统计 / SPA 路由回退 / 未认证拦截。

.DESCRIPTION
    全程只通过 Nginx 暴露的端口访问（默认 80），因此这一步同时验证了
    「Nginx → 后端」「Nginx → MinIO」两条反代链路，而不只是后端本身。
    会自动注册一个随机账号做全链路操作，不污染测试账号数据。

.USAGE
    powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1
    退出码 0 表示全部通过，非 0 表示有失败项。
#>
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$repoRoot = Split-Path -Parent $PSScriptRoot

# ---------- 读取对外端口 ----------
$appPort = 80
$envFile = Join-Path $repoRoot '.env'
if (Test-Path $envFile) {
    $line = Get-Content $envFile | Where-Object { $_ -match '^\s*APP_PORT\s*=' } | Select-Object -First 1
    if ($line) { $appPort = [int]($line -replace '^\s*APP_PORT\s*=\s*', '').Trim() }
}
$suffix = if ($appPort -eq 80) { '' } else { ":$appPort" }
$base   = "http://localhost$suffix"

$script:passed = 0
$script:failed = 0

function Section([string] $title) {
    Write-Host ""
    Write-Host "== $title ==" -ForegroundColor Cyan
}
function Pass([string] $name, [string] $detail = '') {
    $script:passed++
    $suffixText = if ($detail) { " — $detail" } else { '' }
    Write-Host "  [PASS] $name$suffixText" -ForegroundColor Green
}
function Fail([string] $name, [string] $detail) {
    $script:failed++
    Write-Host "  [FAIL] $name — $detail" -ForegroundColor Red
}
function Skip([string] $name, [string] $reason) {
    Write-Host "  [SKIP] $name — $reason" -ForegroundColor Yellow
}

function Get-Utf8Json($response) {
    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    return $text | ConvertFrom-Json
}

function Call([string] $method, [string] $path, $body = $null, [string] $token = '') {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }

    $params = @{
        Uri             = "$base$path"
        Method          = $method
        Headers         = $headers
        UseBasicParsing = $true
        TimeoutSec      = 30
    }
    if ($null -ne $body) {
        $params['Body']        = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 6 -Compress))
        $params['ContentType'] = 'application/json'
    }
    return Invoke-WebRequest @params
}

function Get-StatusCode($errorRecord) {
    try { return [int]$errorRecord.Exception.Response.StatusCode } catch { return -1 }
}

Write-Host "冒烟测试目标：$base"

# ============================================================
Section '链路与静态资源'
# ============================================================
try {
    $r = Call 'GET' '/api/v1/ping'
    $j = Get-Utf8Json $r
    if ($j.code -eq 0 -and $j.data.status -eq 'UP') {
        Pass 'GET /api/v1/ping（Nginx → 后端）' "status=$($j.data.status)"
    } else {
        Fail 'GET /api/v1/ping' "code=$($j.code) status=$($j.data.status)"
    }
} catch { Fail 'GET /api/v1/ping' $_.Exception.Message }

foreach ($route in @('/', '/stats')) {
    try {
        $r = Call 'GET' $route
        $html = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        if ($r.StatusCode -eq 200 -and $html -match '<div id="root"') {
            Pass "GET $route（SPA 路由回退）" '200 且返回 index.html'
        } else {
            Fail "GET $route" "status=$($r.StatusCode)"
        }
    } catch { Fail "GET $route" $_.Exception.Message }
}

# ============================================================
Section '认证全链路'
# ============================================================
$username = 'smoke_' + (Get-Date -Format 'HHmmss') + (Get-Random -Minimum 100 -Maximum 999)
$password = 'smoke-pass-123456'
$token = $null
$refreshToken = $null

try {
    $r = Call 'POST' '/api/v1/auth/register' @{ username = $username; password = $password; nickname = '冒烟测试' }
    $j = Get-Utf8Json $r
    if ($j.code -eq 0 -and $j.data.username -eq $username) {
        Pass '注册' $username
    } else {
        Fail '注册' "code=$($j.code) message=$($j.message)"
    }
} catch { Fail '注册' $_.Exception.Message }

try {
    $r = Call 'POST' '/api/v1/auth/login' @{ username = $username; password = $password }
    $j = Get-Utf8Json $r
    $token = $j.data.accessToken
    $refreshToken = $j.data.refreshToken
    if ($j.code -eq 0 -and $token -and $refreshToken) {
        Pass '登录' "expiresIn=$($j.data.expiresIn)s"
    } else {
        Fail '登录' "code=$($j.code) message=$($j.message)"
    }
} catch { Fail '登录' $_.Exception.Message }

if (-not $token) {
    Write-Host ""
    Write-Host "登录失败，后续用例无法继续。" -ForegroundColor Red
    exit 1
}

try {
    $r = Call 'GET' '/api/v1/users/me' $null $token
    $j = Get-Utf8Json $r
    if ($j.data.username -eq $username) { Pass 'GET /users/me（Bearer 生效）' } else { Fail 'GET /users/me' "返回 $($j.data.username)" }
} catch { Fail 'GET /users/me' $_.Exception.Message }

# ============================================================
Section '日记 CRUD 与统计'
# ============================================================
$diaryId = $null
$title = "冒烟测试日记 $username"

try {
    $r = Call 'POST' '/api/v1/diaries' @{
        title     = $title
        content   = "## 冒烟测试`n`n这是部署验证自动创建的记录。"
        mood      = 3
        weather   = '晴'
        diaryDate = (Get-Date -Format 'yyyy-MM-dd')
    } $token
    $j = Get-Utf8Json $r
    $diaryId = $j.data.id
    if ($j.code -eq 0 -and $diaryId) { Pass '创建日记' "id=$diaryId" } else { Fail '创建日记' "code=$($j.code) message=$($j.message)" }
} catch { Fail '创建日记' $_.Exception.Message }

if ($diaryId) {
    try {
        $r = Call 'GET' "/api/v1/diaries/$diaryId" $null $token
        $j = Get-Utf8Json $r
        if ($j.data.title -eq $title -and $j.data.content) { Pass '日记详情（含 content）' } else { Fail '日记详情' "title=$($j.data.title)" }
    } catch { Fail '日记详情' $_.Exception.Message }

    try {
        $r = Call 'GET' "/api/v1/diaries?page=1&size=10&keyword=$([uri]::EscapeDataString($title))" $null $token
        $j = Get-Utf8Json $r
        $hit = @($j.data.records) | Where-Object { $_.id -eq $diaryId }
        if ($hit) { Pass '列表分页 + 关键词筛选' "total=$($j.data.total) current=$($j.data.current)" }
        else { Fail '列表分页 + 关键词筛选' "未命中 id=$diaryId" }
    } catch { Fail '列表分页 + 关键词筛选' $_.Exception.Message }
}

try {
    $r = Call 'GET' '/api/v1/stats/overview' $null $token
    $j = Get-Utf8Json $r
    if ($j.data.totalCount -ge 1) {
        Pass '统计概览' "totalCount=$($j.data.totalCount) currentStreak=$($j.data.currentStreak)"
    } else {
        Fail '统计概览' "totalCount=$($j.data.totalCount)"
    }
} catch { Fail '统计概览' $_.Exception.Message }

try {
    $r = Call 'GET' '/api/v1/stats/calendar' $null $token
    $j = Get-Utf8Json $r
    Pass '写作热力图' "本年有记录的天数=$(@($j.data).Count)"
} catch { Fail '写作热力图' $_.Exception.Message }

# ============================================================
Section '图片上传（Nginx → MinIO）'
# ============================================================
$imageUrl = $null
try {
    Add-Type -AssemblyName System.Drawing
    $png = Join-Path $env:TEMP "diary-smoke-$username.png"
    $bmp = New-Object System.Drawing.Bitmap 8, 8
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.Clear([System.Drawing.Color]::CornflowerBlue)
    $graphics.Dispose()
    $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()

    $raw = curl.exe -s -X POST "$base/api/v1/files/upload" -H "Authorization: Bearer $token" -F "file=@$png;type=image/png"
    $j = ($raw -join '') | ConvertFrom-Json
    $imageUrl = $j.data.url

    if ($imageUrl -and $imageUrl.StartsWith('/files/')) {
        Pass '上传图片' $imageUrl
    } else {
        Fail '上传图片' "url=$imageUrl message=$($j.message)"
    }

    Remove-Item $png -Force -ErrorAction SilentlyContinue
} catch { Fail '上传图片' $_.Exception.Message }

if ($imageUrl) {
    try {
        $r = Call 'GET' $imageUrl
        if ($r.StatusCode -eq 200 -and $r.RawContentLength -gt 0) {
            Pass '通过 /files/ 取回图片（Nginx → MinIO）' "$($r.RawContentLength) 字节"
        } else {
            Fail '通过 /files/ 取回图片' "status=$($r.StatusCode)"
        }
    } catch { Fail '通过 /files/ 取回图片' $_.Exception.Message }
}

# ============================================================
Section '令牌刷新、删除与登出'
# ============================================================
try {
    $r = Call 'POST' '/api/v1/auth/refresh' @{ refreshToken = $refreshToken }
    $j = Get-Utf8Json $r
    if ($j.data.accessToken) {
        $token = $j.data.accessToken
        $refreshToken = $j.data.refreshToken
        Pass '刷新令牌'
    } else {
        Fail '刷新令牌' "code=$($j.code) message=$($j.message)"
    }
} catch { Fail '刷新令牌' $_.Exception.Message }

if ($diaryId) {
    try {
        $r = Call 'DELETE' "/api/v1/diaries/$diaryId" $null $token
        Pass '删除日记（逻辑删除）'
    } catch { Fail '删除日记' $_.Exception.Message }
}

try {
    $r = Call 'POST' '/api/v1/auth/logout' @{ refreshToken = $refreshToken }
    Pass '登出'
} catch { Fail '登出' $_.Exception.Message }

# ============================================================
Section '未认证拦截'
# ============================================================
try {
    $null = Call 'GET' '/api/v1/diaries'
    Fail '未带 token 访问 /diaries 应被拒绝' '却返回了成功'
} catch {
    $code = Get-StatusCode $_
    if ($code -eq 401) { Pass '未认证访问返回 401' } else { Fail '未认证访问返回 401' "实际 $code" }
}

# ============================================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ("通过 {0} 项，失败 {1} 项" -f $script:passed, $script:failed) -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
Write-Host "============================================" -ForegroundColor Cyan

if ($script:failed -gt 0) { exit 1 }
