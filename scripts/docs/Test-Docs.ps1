#Requires -Version 7.0
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$docRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$testParent = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '.test-work'))
$fixture = Join-Path $testParent ([guid]::NewGuid().ToString('N'))
$utf8 = [Text.UTF8Encoding]::new($false)
$passed = 0

function Write-Fixture([string]$path, [string]$value) {
    $target = Join-Path $fixture $path
    $null = [IO.Directory]::CreateDirectory((Split-Path $target))
    [IO.File]::WriteAllText($target, $value, $utf8)
}

function Invoke-FixtureGit([string[]]$flags) {
    $result = @(& git -C $fixture @flags 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Fixture git failed: $($result -join ' ')" }
}

function Expect-Check([string]$name, [string[]]$flags, [int]$exitCode, [string]$pattern = 'PASS:') {
    $output = @(& pwsh -NoProfile -File (Join-Path $fixture 'scripts/docs/Manage-Docs.ps1') @flags 2>&1)
    $actual = $LASTEXITCODE
    if ($actual -ne $exitCode -or ($output -join "`n") -notmatch $pattern) {
        throw "FAIL ${name}: expected exit=$exitCode / $pattern, got $actual`n$($output -join "`n")"
    }
    $script:passed++
    Write-Host "PASS $name"
}

function Read-FixtureText([string]$path) {
    # Compare and restore content with CRLF/CR normalized to LF so assertions stay
    # independent of the checkout line-ending style: fresh Windows clones check out
    # CRLF while Sync rewrites generated blocks with LF. This mirrors how
    # Manage-Docs reads documents and does not weaken any comparison.
    [IO.File]::ReadAllText((Join-Path $fixture $path)).Replace("`r`n", "`n").Replace("`r", "")
}

try {
    if (-not $fixture.StartsWith($docRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Fixture must stay inside the project.'
    }
    $null = [IO.Directory]::CreateDirectory($fixture)
    # Only copy repository source/documentation inputs, never local credentials or build outputs.
    $paths = @(& git -C $docRoot -c core.quotepath=false ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inventory source fixtures.' }
    foreach ($path in ($paths | Sort-Object -Unique)) {
        if ($path -match '(^|/)(\.test-work|build|dist)/|(^|/)(local\.properties|\.env)$|\.(jks|keystore)$') { continue }
        $source = Join-Path $docRoot $path
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { continue }
        $target = Join-Path $fixture $path
        $null = [IO.Directory]::CreateDirectory((Split-Path $target))
        Copy-Item -LiteralPath $source -Destination $target
    }
    Invoke-FixtureGit @('-c', 'init.defaultBranch=main', 'init', '--quiet')
    Invoke-FixtureGit @('config', '--local', 'core.autocrlf', 'false')
    Expect-Check 'baseline' @('-Mode', 'Check') 0
    $originalIndex = Read-FixtureText 'docs/index.md'
    $archiveIndexPath = Join-Path $fixture 'docs/archive/index.md'
    $originalArchiveIndex = if (Test-Path -LiteralPath $archiveIndexPath -PathType Leaf) { Read-FixtureText 'docs/archive/index.md' } else { $null }
    $originalDev = Read-FixtureText 'docs/development.md'
    $originalTodo = Read-FixtureText 'docs/todo.md'
    $originalEntry = Read-FixtureText 'AGENTS.md'
    $originalApp = Read-FixtureText 'app/build.gradle.kts'
    if ($originalIndex -match '\]\([^)]*\.agents/skills/') { throw 'Document index must not link to Skills.' }
    Expect-Check 'sync idempotence' @('-Mode', 'Sync') 0
    $archiveUnchanged = $null -eq $originalArchiveIndex -or $originalArchiveIndex -ceq (Read-FixtureText 'docs/archive/index.md')
    if ($originalIndex -cne (Read-FixtureText 'docs/index.md') -or
        -not $archiveUnchanged -or
        $originalDev -cne (Read-FixtureText 'docs/development.md')) { throw 'Sync changed up-to-date generated files.' }

    $newDoc = "---`ntitle: 测试页面`npurpose: 验证自动登记`nstatus: 草案`nowner: 测试 Agent`nscope: 隔离仓库`nupdated: 2026-09-11`nverification: 未验证`nverified: 测试样本`n---`n`n# 测试页面`n"
    Write-Fixture 'docs/测试 页面.md' $newDoc
    Expect-Check 'unregistered new document' @('-Mode', 'Check') 1 'DOC-GENERATED|DOC-REACHABLE'
    Expect-Check 'automatically register Unicode and spaces' @('-Mode', 'Sync') 0
    $null = [IO.Directory]::CreateDirectory((Join-Path $fixture 'docs/topics'))
    Move-Item -LiteralPath (Join-Path $fixture 'docs/测试 页面.md') -Destination (Join-Path $fixture 'docs/topics/renamed.md')
    Expect-Check 'rename updates index' @('-Mode', 'Sync') 0
    Remove-Item -LiteralPath (Join-Path $fixture 'docs/topics/renamed.md')
    Expect-Check 'deletion updates index' @('-Mode', 'Sync') 0

    Write-Fixture 'docs/todo.md' ($originalTodo + "`n[broken](missing.md)`n")
    Expect-Check 'broken local link' @('-Mode', 'Check') 1 'DOC-LINK'
    Write-Fixture 'docs/todo.md' ($originalTodo + "`n[valid](index.md#按任务阅读)`n")
    Expect-Check 'valid heading anchor' @('-Mode', 'Check') 0
    Write-Fixture 'docs/todo.md' ($originalTodo + "`n[broken](index.md#missing-heading)`n")
    Expect-Check 'broken heading anchor' @('-Mode', 'Check') 1 'DOC-LINK'
    Write-Fixture 'docs/todo.md' ($originalTodo + "`n[escape](../../outside.md)`n")
    Expect-Check 'link outside project' @('-Mode', 'Check') 1 'DOC-LINK'
    Write-Fixture 'docs/todo.md' ([regex]::Replace($originalTodo, '(?m)^owner:.*\r?\n', ''))
    Expect-Check 'missing metadata' @('-Mode', 'Check') 1 'DOC-META'
    Write-Fixture 'docs/todo.md' ($originalTodo + "`n[Skill](../.agents/skills/write-execution-plan/SKILL.md)`n")
    Expect-Check 'document links to Skills are prohibited' @('-Mode', 'Check') 1 'DOC-LINK.*must not link to Skills'
    Write-Fixture 'docs/todo.md' $originalTodo

    Write-Fixture 'orphan.md' $newDoc
    Expect-Check 'document outside formal directory' @('-Mode', 'Check') 1 'DOC-SCOPE'
    Remove-Item -LiteralPath (Join-Path $fixture 'orphan.md')
    Write-Fixture 'docs/ignored.md' $newDoc
    Write-Fixture '.git/info/exclude' "/docs/ignored.md`n"
    Expect-Check 'ignored formal document' @('-Mode', 'Check') 1 'DOC-SCOPE'
    Remove-Item -LiteralPath (Join-Path $fixture 'docs/ignored.md')
    Write-Fixture '.git/info/exclude' ''

    Write-Fixture 'AGENTS.md' ([regex]::Replace($originalEntry, '\[[^\]]+\]\(docs/[^)]+\)', 'route removed for test'))
    Expect-Check 'entry disconnected from index' @('-Mode', 'Check') 1 'DOC-REACHABLE'
    Write-Fixture 'AGENTS.md' $originalEntry

    $changedApp = [regex]::Replace($originalApp, 'versionCode\s*=\s*\d+', 'versionCode = 999')
    Write-Fixture 'app/build.gradle.kts' $changedApp
    Expect-Check 'configuration drift' @('-Mode', 'Check') 1 'DOC-GENERATED'
    Expect-Check 'refresh configuration from source' @('-Mode', 'Sync') 0
    Write-Fixture 'app/build.gradle.kts' $originalApp
    Expect-Check 'restore configuration' @('-Mode', 'Sync') 0

    $skillPath = '.agents/skills/write-execution-plan/SKILL.md'
    $originalSkill = Read-FixtureText $skillPath
    Write-Fixture $skillPath ([regex]::Replace($originalSkill, '(?m)^name:.*$', 'name: wrong-folder'))
    Expect-Check 'skill name must match directory' @('-Mode', 'Check') 1 'DOC-META'
    Write-Fixture $skillPath ([regex]::Replace($originalSkill, '(?m)^description:.*\r?\n', ''))
    Expect-Check 'skill description is required' @('-Mode', 'Check') 1 'DOC-META'
    Write-Fixture $skillPath ([regex]::Replace($originalSkill, '(?m)^  owner:.*\r?\n', ''))
    Expect-Check 'skill governance metadata is required' @('-Mode', 'Check') 1 'DOC-META'
    Write-Fixture $skillPath ($originalSkill + "`n[broken](../../../docs/missing.md)`n")
    Expect-Check 'skill links are validated' @('-Mode', 'Check') 1 'DOC-LINK'
    Write-Fixture $skillPath $originalSkill
    $newSkillPath = '.agents/skills/test-discovery/SKILL.md'
    Write-Fixture $newSkillPath ($originalSkill.Replace('name: write-execution-plan', 'name: test-discovery'))
    Write-Fixture '.git/info/exclude' "/$newSkillPath`n"
    Expect-Check 'ignored skill is detected' @('-Mode', 'Check') 1 'DOC-SCOPE'
    Write-Fixture '.git/info/exclude' ''
    Expect-Check 'new skill needs no document index link' @('-Mode', 'Check') 0
    Expect-Check 'new skill leaves index unchanged' @('-Mode', 'Sync') 0
    if ($originalIndex -cne [IO.File]::ReadAllText((Join-Path $fixture 'docs/index.md'))) { throw 'Skill changed the document index.' }
    Remove-Item -LiteralPath (Join-Path $fixture $newSkillPath)
    Expect-Check 'skill removal leaves index unchanged' @('-Mode', 'Check') 0

    # Archive index regressions: a pre-built empty index is rejected, archive documents
    # require an index, and Sync fills the index once the skeleton exists and the
    # documentation nav reaches it. Sync fills generated blocks but never creates the
    # index file, so the skeleton mirrors the documented maintainer workflow.
    $archiveSkeleton = "---`ntitle: 测试归档文档索引`npurpose: 验证归档索引生命周期`nstatus: 当前有效`nowner: 测试 Agent`nscope: 隔离仓库归档索引`nupdated: 2026-09-11`nverification: 未验证`nverified: 测试样本`n---`n`n# 测试归档文档索引`n`n<!-- docs:archive:start -->`n| 归档文档 | 用途 | 生命周期 | 验证状态 |`n| --- | --- | --- | --- |`n<!-- docs:archive:end -->`n"
    $originalDocumentation = Read-FixtureText 'docs/documentation.md'
    $navWithArchive = $originalDocumentation.Replace(
        '[文档总索引](index.md) · [当前问题与解决进度](quality.md) · [Agent 工作入口](../AGENTS.md)',
        '[文档总索引](index.md) · [当前问题与解决进度](quality.md) · [归档文档索引](archive/index.md) · [Agent 工作入口](../AGENTS.md)')
    if ($navWithArchive -ceq $originalDocumentation) { throw 'documentation.md nav line not found for archive regression.' }
    Write-Fixture 'docs/archive/index.md' $archiveSkeleton
    Expect-Check 'pre-built empty archive index is rejected' @('-Mode', 'Check') 1 'DOC-SCOPE docs/archive/index.md'
    Remove-Item -LiteralPath (Join-Path $fixture 'docs/archive/index.md')
    Expect-Check 'empty archive index removal restores check' @('-Mode', 'Check') 0
    Write-Fixture 'docs/archive/case-a.md' $newDoc.Replace('status: 草案', 'status: 历史归档')
    Expect-Check 'archive document requires index' @('-Mode', 'Check') 1 'DOC-SOURCE docs/archive/index.md'
    Write-Fixture 'docs/archive/index.md' $archiveSkeleton
    Write-Fixture 'docs/documentation.md' $navWithArchive
    Expect-Check 'sync fills archive index' @('-Mode', 'Sync') 0
    Expect-Check 'archive index reachable with three generated blocks' @('-Mode', 'Check') 0 'PASS:.*generatedBlocks=3'
    if ((Read-FixtureText 'docs/archive/index.md') -notmatch 'case-a\.md' -or
        ($originalIndex -ceq (Read-FixtureText 'docs/index.md'))) { throw 'Sync did not register the archive document and index.' }
    Remove-Item -LiteralPath (Join-Path $fixture 'docs/archive') -Recurse -Force
    Write-Fixture 'docs/documentation.md' $originalDocumentation
    Expect-Check 'archive removal resyncs index' @('-Mode', 'Sync') 0
    Expect-Check 'archive state returns to two generated blocks' @('-Mode', 'Check') 0 'PASS:.*generatedBlocks=2'
    if ($originalIndex -cne (Read-FixtureText 'docs/index.md')) { throw 'Archive regression left a stale document index.' }

    Invoke-FixtureGit @('add', '--all')
    Expect-Check 'valid staged snapshot' @('-Mode', 'Check', '-Staged') 0
    Write-Fixture $skillPath ([regex]::Replace($originalSkill, '(?m)^name:.*$', 'name: wrong-folder'))
    Invoke-FixtureGit @('add', '--', $skillPath)
    Write-Fixture $skillPath $originalSkill
    Expect-Check 'staged skill error survives working-tree repair' @('-Mode', 'Check', '-Staged') 1 'DOC-META'
    Invoke-FixtureGit @('add', '--', $skillPath)
    Write-Fixture 'docs/todo.md' ($originalTodo + "`n[broken](missing.md)`n")
    Invoke-FixtureGit @('add', '--', 'docs/todo.md')
    Write-Fixture 'docs/todo.md' $originalTodo
    Expect-Check 'working tree repaired' @('-Mode', 'Check') 0
    Expect-Check 'staged omission still fails' @('-Mode', 'Check', '-Staged') 1 'DOC-LINK'

    Invoke-FixtureGit @('config', '--local', 'core.hooksPath', '.githooks')
    Invoke-FixtureGit @('update-index', '--chmod=+x', '.githooks/pre-commit')
    if (-not $IsWindows) { & chmod +x (Join-Path $fixture '.githooks/pre-commit') }
    $hookOutput = @(& git -C $fixture -c user.name=DocsTest -c user.email=docs-test@example.invalid commit -m 'test(docs): reject broken staged links' 2>&1)
    if ($LASTEXITCODE -eq 0 -or ($hookOutput -join "`n") -notmatch 'DOC-LINK') { throw "Pre-commit did not block invalid snapshot: $hookOutput" }
    $passed++
    Write-Host 'PASS pre-commit blocks invalid staged content'
    Invoke-FixtureGit @('add', '--', 'docs/todo.md')
    Expect-Check 'repaired staged snapshot' @('-Mode', 'Check', '-Staged') 0
    Write-Host "PASS: $passed documentation regression scenarios."
} finally {
    # Never recursively delete an unchecked or computed location outside this fixture parent.
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    if (-not $resolvedFixture.StartsWith($testParent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not $testParent.StartsWith($docRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing cleanup outside the project test directory.'
    }
    if (Test-Path -LiteralPath $resolvedFixture) { Remove-Item -LiteralPath $resolvedFixture -Recurse -Force }
    if ((Test-Path -LiteralPath $testParent) -and @(Get-ChildItem -LiteralPath $testParent -Force).Count -eq 0) {
        Remove-Item -LiteralPath $testParent
    }
}
