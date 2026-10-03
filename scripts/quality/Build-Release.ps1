#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$CandidateApk,
    [switch]$VerifyOnly,
    [switch]$RegisterBaseline
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$baselineCertFile = Join-Path $repoRoot 'scripts/quality/release-certificate.sha256'
$distDir = Join-Path $repoRoot 'dist'

function Get-AndroidSdkTool([string]$toolName) {
    $sdkDir = $null
    $localProps = Join-Path $repoRoot 'local.properties'
    if (Test-Path -LiteralPath $localProps -PathType Leaf) {
        $lines = Get-Content -LiteralPath $localProps
        foreach ($line in $lines) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                $rawPath = $matches[1].Trim().Replace('\:', ':').Replace('\\', '\')
                if (Test-Path -LiteralPath $rawPath -PathType Container) {
                    $sdkDir = $rawPath
                    break
                }
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($sdkDir)) {
        if ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME -PathType Container)) {
            $sdkDir = $env:ANDROID_HOME
        } elseif ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT -PathType Container)) {
            $sdkDir = $env:ANDROID_SDK_ROOT
        }
    }
    if ([string]::IsNullOrWhiteSpace($sdkDir)) {
        throw "无法定位 Android SDK 目录（请检查 local.properties 或 ANDROID_HOME 环境变量）"
    }

    $buildToolsDir = Join-Path $sdkDir 'build-tools'
    if (-not (Test-Path -LiteralPath $buildToolsDir -PathType Container)) {
        throw "未找到 build-tools 目录: $buildToolsDir"
    }

    $versions = Get-ChildItem -LiteralPath $buildToolsDir -Directory | Sort-Object Name -Descending
    foreach ($ver in $versions) {
        $candidate = Join-Path $ver.FullName $toolName
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return $candidate
        }
    }
    throw "在 build-tools 中未找到工具: $toolName"
}

$apksigner = Get-AndroidSdkTool 'apksigner.bat'
$aapt = Get-AndroidSdkTool 'aapt.exe'

# 1. 如果不是仅验证模式，先检查并执行 Release 构建
if (-not $VerifyOnly) {
    $keystorePath = Join-Path $repoRoot '.signing/todo-release.jks'
    if (-not (Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
        throw "发行密钥文件不存在: $keystorePath。构建拒绝。"
    }
    if ([string]::IsNullOrWhiteSpace($env:STORE_PASSWORD)) {
        $securePass = Read-Host -Prompt "请输入发行密钥库密码 (输入内容不回显)" -AsSecureString
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePass)
        try {
            $env:STORE_PASSWORD = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
        } finally {
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
    if ([string]::IsNullOrWhiteSpace($env:KEY_PASSWORD)) {
        $env:KEY_PASSWORD = $env:STORE_PASSWORD
    }
    if ([string]::IsNullOrWhiteSpace($env:STORE_PASSWORD)) {
        throw "未提供有效的发行密钥密码。构建拒绝。"
    }

    if (-not $RegisterBaseline -and -not (Test-Path -LiteralPath $baselineCertFile -PathType Leaf)) {
        throw "公开发行证书基线文件不存在: $baselineCertFile。若首次构建请使用 -RegisterBaseline 参数。"
    }

    Write-Host "开始执行 Release 构建 (assembleRelease)..."
    $gradlew = if ($IsWindows) { Join-Path $repoRoot 'gradlew.bat' } else { Join-Path $repoRoot 'gradlew' }
    & $gradlew :app:assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle assembleRelease 执行失败 (退出码 $LASTEXITCODE)"
    }
}

# 2. 定位待验证的候选 APK
if ([string]::IsNullOrWhiteSpace($CandidateApk)) {
    $CandidateApk = Join-Path $repoRoot 'app/build/outputs/apk/release/app-release.apk'
}
if (-not (Test-Path -LiteralPath $CandidateApk -PathType Leaf)) {
    throw "未找到候选 APK 文件: $CandidateApk"
}

# 3. 验证 APK 签名与证书指纹
Write-Host "验证 APK 签名与证书指纹: $CandidateApk"
$sigRaw = & $apksigner verify --print-certs $CandidateApk 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "apksigner 签名验证未通过: $($sigRaw -join "`n")"
}

$sigText = $sigRaw -join "`n"
$sha256Matches = [regex]::Matches($sigText, 'Signer\s*#(\d+)\s+certificate\s+SHA-256\s+digest:\s*([0-9a-fA-F]{64})')
if ($sha256Matches.Count -ne 1) {
    throw "签名者数量异常，预期恰好 1 个有效签名者，实际检测到 $($sha256Matches.Count) 个签名者"
}
$actualCertSha256 = $sha256Matches[0].Groups[2].Value.ToLowerInvariant()

$oldDebugFingerprint = "730658f6e0a35a8ef08aa850a276733a71d884fc7e25fab689c7be00d5a45110"
if ($actualCertSha256 -eq $oldDebugFingerprint) {
    throw "检测到旧调试证书签名 (SHA-256: $actualCertSha256)！严禁用调试证书冒充正式发行证书交付。"
}

if ($RegisterBaseline) {
    Write-Host "首次登记公开发行证书基线 -> $baselineCertFile"
    Set-Content -LiteralPath $baselineCertFile -Value $actualCertSha256 -NoNewline
    Write-Host "已登记发行证书 SHA-256: $actualCertSha256"
}

if (-not (Test-Path -LiteralPath $baselineCertFile -PathType Leaf)) {
    throw "证书基线文件缺失: $baselineCertFile。无法核对发行证书。"
}
$baselineContent = (Get-Content -LiteralPath $baselineCertFile -Raw).Trim().ToLowerInvariant()
if ($baselineContent.Length -ne 64 -or $baselineContent -notmatch '^[0-9a-f]{64}$') {
    throw "证书基线格式错误: 必须为单行 64 位十六进制 SHA-256 字符串"
}
if ($actualCertSha256 -ne $baselineContent) {
    throw "证书指纹与登记基线不符！`n  APK 证书 SHA-256: $actualCertSha256`n  登记基线 SHA-256: $baselineContent`n拒绝交付。"
}

# 4. 从 Manifest 本身与元数据核对包名、版本
Write-Host "核对 APK Manifest 元数据..."
$badgingRaw = & $aapt dump badging $CandidateApk 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "aapt dump badging 读取失败: $($badgingRaw -join "`n")"
}
$badgingText = $badgingRaw -join "`n"
if ($badgingText -notmatch "package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'") {
    throw "无法从 APK badging 解析包名与版本信息"
}
$apkPackage = $matches[1]
$apkVersionCode = $matches[2]
$apkVersionName = $matches[3]

if ($apkPackage -ne 'com.aistudio.todo.xqwdfa') {
    throw "APK 包名不符，预期 com.aistudio.todo.xqwdfa，实际为 $apkPackage"
}
if ($apkVersionCode -ne '20') {
    throw "APK versionCode 不符，预期 20，实际为 $apkVersionCode"
}
if ($apkVersionName -ne '3.0.0') {
    throw "APK versionName 不符，预期 3.0.0，实际为 $apkVersionName"
}

# 计算候选 APK SHA-256 哈希
$candidateHash = (Get-FileHash -LiteralPath $CandidateApk -Algorithm SHA256).Hash.ToUpperInvariant()

if ($VerifyOnly) {
    Write-Host "PASS: 候选 APK 验证全部通过 (VerifyOnly 模式)。"
    Write-Host "  包名: $apkPackage"
    Write-Host "  版本: $apkVersionName ($apkVersionCode)"
    Write-Host "  证书 SHA-256: $actualCertSha256"
    Write-Host "  APK SHA-256:  $candidateHash"
    exit 0
}

# 5. 复制到 dist/ 并核对
if (-not (Test-Path -LiteralPath $distDir -PathType Container)) {
    New-Item -ItemType Directory -LiteralPath $distDir -Force | Out-Null
}
$targetApk = Join-Path $distDir "todo-v${apkVersionName}-release.apk"

if (Test-Path -LiteralPath $targetApk -PathType Leaf) {
    $existingHash = (Get-FileHash -LiteralPath $targetApk -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($existingHash -ne $candidateHash) {
        throw "目标文件已存在且 SHA-256 哈希不一致！`n  已有文件: $existingHash`n  本次候选: $candidateHash`n为保护已有产物，拒绝覆盖！"
    }
    Write-Host "目标文件已存在且哈希完全一致，保持幂等。"
} else {
    Copy-Item -LiteralPath $CandidateApk -Destination $targetApk -Force
    if (-not (Test-Path -LiteralPath $targetApk -PathType Leaf)) {
        throw "复制 APK 到 dist 失败: $targetApk"
    }
    $targetHash = (Get-FileHash -LiteralPath $targetApk -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($targetHash -ne $candidateHash) {
        throw "复制后目标文件哈希 ($targetHash) 与候选哈希 ($candidateHash) 不一致！"
    }
    Write-Host "成功复制并校验交付产物: $targetApk"
}

Write-Host "=== Release 交付验证报告 ==="
Write-Host "产物文件:      $targetApk"
Write-Host "应用包名:      $apkPackage"
Write-Host "应用版本:      $apkVersionName (versionCode: $apkVersionCode)"
Write-Host "公开发行证书:  $actualCertSha256"
Write-Host "产物 SHA-256:  $candidateHash"
Write-Host "============================"
exit 0
