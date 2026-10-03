#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Commit', 'Full')]
    [string]$Mode = 'Full'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$docScript = Join-Path $repoRoot 'scripts/docs/Manage-Docs.ps1'
$docTestScript = Join-Path $repoRoot 'scripts/docs/Test-Docs.ps1'

if (-not (Test-Path -LiteralPath $docScript -PathType Leaf)) {
    Write-Error "缺少文档管理脚本: $docScript"
    exit 1
}

$gradlew = if ($IsWindows) {
    Join-Path $repoRoot 'gradlew.bat'
} else {
    Join-Path $repoRoot 'gradlew'
}

function Test-IsPureDocPath([string]$path) {
    $norm = $path.Replace('\', '/').TrimStart('/')
    if ($norm -match '^docs/.*\.md$') { return $true }
    if ($norm -eq 'AGENTS.md') { return $true }
    # README.md and the standard LICENSE are not governed Markdown, but they are
    # documentation inputs: editing them alone must not trigger the Gradle gates.
    if ($norm -eq 'README.md') { return $true }
    if ($norm -eq 'LICENSE') { return $true }
    if ($norm -match '^\.agents/skills/[^/]+/SKILL\.md$') { return $true }
    return $false
}

function Get-GitNulPaths([string[]]$gitArgs) {
    $raw = & git -C $repoRoot -c core.quotepath=false @gitArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Git 命令执行失败: git $($gitArgs -join ' ')"
        exit 1
    }
    $text = ($raw -join "`n")
    if ([string]::IsNullOrWhiteSpace($text)) { return [string[]]@() }
    $items = $text.Split([char]0, [System.StringSplitOptions]::RemoveEmptyEntries)
    $list = [System.Collections.Generic.List[string]]::new()
    foreach ($item in $items) {
        $t = $item.Trim().Replace('\', '/')
        if ($t.Length -gt 0) { $list.Add($t) }
    }
    return [string[]]$list.ToArray()
}

function Report-LintSummary() {
    $lintXml = Join-Path $repoRoot 'app/build/reports/lint-results-debug.xml'
    if (Test-Path -LiteralPath $lintXml -PathType Leaf) {
        try {
            $xml = [xml](Get-Content -LiteralPath $lintXml -Raw)
            $issues = $xml.issues.issue
            $count = if ($null -eq $issues) { 0 } elseif ($issues -is [array]) { $issues.Count } else { 1 }
            Write-Host "LintDebug 检查完成: $count 个警告，0 个错误。"
        } catch {
            Write-Host "LintDebug 检查完成。"
        }
    }
}

if ($Mode -eq 'Full') {
    Write-Host "=== 开始执行本地 Full 门禁检查 (工作区全量) ==="

    Write-Host "1/3 运行文档完整检查..."
    & pwsh -NoProfile -File $docScript -Mode Check
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Full 门禁: 文档检查未通过 (Manage-Docs.ps1 -Mode Check)"
        exit 1
    }

    Write-Host "2/3 运行工作区空白字符与格式检查 (git diff --check)..."
    & git -C $repoRoot diff --check
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Full 门禁: 工作区存在未提交的格式或空白字符问题 (git diff --check)"
        exit 1
    }

    Write-Host "3/3 运行 Gradle 单元测试与 lintDebug..."
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
        Write-Error "Full 门禁: 未找到 Gradle wrapper: $gradlew"
        exit 1
    }

    & $gradlew :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Full 门禁: Gradle 单元测试或 lintDebug 失败 (退出码 $LASTEXITCODE)。报告位置: app/build/reports/tests/testDebugUnitTest/index.html 与 app/build/reports/lint-results-debug.html"
        exit 1
    }
    Report-LintSummary

    Write-Host "PASS: 本地 Full 门禁检查全部通过。"
    exit 0
}

# ================= Commit 模式 =================
Write-Host "=== 开始执行本地 Commit 门禁检查 ==="

# 1. 检查文档脚本与钩子自身是否有未暂存修改
$unstagedAll = @(Get-GitNulPaths @('diff', '--name-only', '-z'))
$selfModified = @($unstagedAll | Where-Object {
    $_ -match '^scripts/docs/' -or $_ -match '^\.githooks/' -or $_ -match '^scripts/quality/'
})
if ($selfModified.Count -gt 0) {
    Write-Error "门禁阻断: 文档/门禁脚本自身在工作区存在未暂存的修改或删除: $($selfModified -join ', ')。必须先完整暂存门禁工具自身修改，以确保执行的检查器属于提交版本。"
    exit 1
}

# 2. 对暂存区运行受管文档检查与格式检查
Write-Host "1/5 检查暂存区受管文档 (Manage-Docs.ps1 -Mode Check -Staged)..."
& pwsh -NoProfile -File $docScript -Mode Check -Staged
if ($LASTEXITCODE -ne 0) {
    Write-Error "门禁阻断: 暂存区文档门禁检查未通过"
    exit 1
}

Write-Host "2/5 检查暂存区格式 (git diff --cached --check)..."
& git -C $repoRoot diff --cached --check
if ($LASTEXITCODE -ne 0) {
    Write-Error "门禁阻断: 暂存区存在格式或空白字符问题 (git diff --cached --check)"
    exit 1
}

# 3. 分析暂存路径，判断是否纯文档
$stagedPaths = @(Get-GitNulPaths @('diff', '--cached', '--name-only', '-z'))
if ($stagedPaths.Count -eq 0) {
    Write-Host "暂存区无变更，跳过后续代码检查。"
    exit 0
}

$nonDocStaged = @($stagedPaths | Where-Object { -not (Test-IsPureDocPath $_) })
if ($nonDocStaged.Count -eq 0) {
    Write-Host "暂存变更全部属于受管文档 ($($stagedPaths.Count) 篇文档)，跳过 Gradle 测试与 lintDebug。"
    Write-Host "PASS: 本地 Commit 门禁检查通过 (纯文档模式)。"
    exit 0
}

Write-Host "检测到代码/配置变更 ($($nonDocStaged.Count) 个非文档文件)，触发完整代码门禁..."

# 4. 检查工作区与暂存区一致性（仅限非文档路径）
$unstagedNonDoc = @($unstagedAll | Where-Object { -not (Test-IsPureDocPath $_) })
if ($unstagedNonDoc.Count -gt 0) {
    Write-Error "门禁阻断: 受跟踪的代码/配置文件在工作区存在未暂存修改或删除: $($unstagedNonDoc -join ', ')。为防止工作区代码状态掩盖暂存代码缺陷，代码相关输入必须暂存完整一致。"
    exit 1
}

# 检查未跟踪非忽略输入（除约定交付物 dist/ 与测试目录外）
$untrackedAll = @(Get-GitNulPaths @('ls-files', '--others', '--exclude-standard', '-z'))
$blockedUntracked = @()
foreach ($p in $untrackedAll) {
    if ($p -match '^dist(/|$)' -or
        $p -match '^scripts/docs/\.test-work(/|$)' -or
        $p -match '^scripts/quality/\.test-work(/|$)' -or
        $p -match '^\.test-gradle') {
        continue
    }
    if (Test-IsPureDocPath $p) {
        continue
    }
    $blockedUntracked += $p
}
if ($blockedUntracked.Count -gt 0) {
    Write-Error "门禁阻断: 检测到未跟踪的代码或配置文件: $($blockedUntracked -join ', ')。未知非文档输入默认阻断，请暂存或加入 .gitignore。"
    exit 1
}

# 5. 记录开始时的暂存 tree
$treeBeforeRaw = & git -C $repoRoot write-tree
if ($LASTEXITCODE -ne 0) {
    Write-Error "门禁阻断: git write-tree 失败"
    exit 1
}
$treeBefore = ($treeBeforeRaw -join '').Trim()

# 6. 运行 Gradle 单元测试与 lintDebug
Write-Host "3/5 运行 Gradle 单元测试与 lintDebug (:app:testDebugUnitTest :app:lintDebug)..."
if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
    Write-Error "门禁阻断: 未找到 Gradle wrapper: $gradlew"
    exit 1
}

& $gradlew :app:testDebugUnitTest :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) {
    Write-Error "门禁阻断: Gradle 单元测试或 lintDebug 失败 (退出码 $LASTEXITCODE)。单元测试报告: app/build/reports/tests/testDebugUnitTest/index.html，lint 报告: app/build/reports/lint-results-debug.html"
    exit 1
}
Report-LintSummary

# 7. 若暂存涉及 scripts/docs/ 或 .githooks/，额外运行文档工具回归
$touchesDocsTools = @($stagedPaths | Where-Object { $_ -match '^scripts/docs/' -or $_ -match '^\.githooks/' })
if ($touchesDocsTools.Count -gt 0) {
    Write-Host "4/5 暂存涉及文档脚本或钩子，运行文档工具隔离回归 (Test-Docs.ps1)..."
    if (Test-Path -LiteralPath $docTestScript -PathType Leaf) {
        & pwsh -NoProfile -File $docTestScript
        if ($LASTEXITCODE -ne 0) {
            Write-Error "门禁阻断: 文档工具隔离回归失败 (Test-Docs.ps1)"
            exit 1
        }
    }
} else {
    Write-Host "4/5 暂存未修改文档工具自身，跳过文档工具回归。"
}

# 8. 校验检查期间未发生输入变动
Write-Host "5/5 核验检查期间输入无篡改/变动..."
$treeAfterRaw = & git -C $repoRoot write-tree
if ($LASTEXITCODE -ne 0) {
    Write-Error "门禁阻断: 结束时 git write-tree 失败"
    exit 1
}
$treeAfter = ($treeAfterRaw -join '').Trim()
if ($treeBefore -ne $treeAfter) {
    Write-Error "门禁阻断: 门禁检查期间暂存区树标识发生变动 ($treeBefore -> $treeAfter)，请重新运行提交检查。"
    exit 1
}

$unstagedAfter = @(Get-GitNulPaths @('diff', '--name-only', '-z'))
$unstagedAfterNonDoc = @($unstagedAfter | Where-Object { -not (Test-IsPureDocPath $_) })
if ($unstagedAfterNonDoc.Count -gt 0) {
    Write-Error "门禁阻断: 门禁检查期间相关工作区发生非文档代码修改: $($unstagedAfterNonDoc -join ', ')，请重新运行提交检查。"
    exit 1
}

$untrackedAfter = @(Get-GitNulPaths @('ls-files', '--others', '--exclude-standard', '-z'))
$blockedUntrackedAfter = @()
foreach ($p in $untrackedAfter) {
    if ($p -match '^dist(/|$)' -or
        $p -match '^scripts/docs/\.test-work(/|$)' -or
        $p -match '^scripts/quality/\.test-work(/|$)' -or
        $p -match '^\.test-gradle') {
        continue
    }
    if (Test-IsPureDocPath $p) {
        continue
    }
    $blockedUntrackedAfter += $p
}
if ($blockedUntrackedAfter.Count -gt 0) {
    Write-Error "门禁阻断: 门禁检查期间检测到新增未跟踪代码或配置文件: $($blockedUntrackedAfter -join ', ')，请重新运行提交检查。"
    exit 1
}

Write-Host "PASS: 本地 Commit 门禁检查全部通过。"
exit 0
