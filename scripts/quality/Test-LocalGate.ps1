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

function Write-Fixture([string]$relPath, [string]$content) {
    $target = Join-Path $fixture $relPath
    $parent = Split-Path $target
    if (-not (Test-Path -LiteralPath $parent)) {
        $null = [IO.Directory]::CreateDirectory($parent)
    }
    [IO.File]::WriteAllText($target, $content, $utf8)
}

function Invoke-FixtureGit([string[]]$gitArgs) {
    $result = @(& git -C $fixture -c core.quotepath=false @gitArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Fixture git 失败: git $($gitArgs -join ' ')`n$($result -join "`n")"
    }
    return $result
}

function Invoke-FixtureCommit([string]$msg) {
    $output = @(& git -C $fixture -c user.name=GateTester -c user.email=tester@example.invalid -c core.quotepath=false commit -m $msg 2>&1)
    $code = $LASTEXITCODE
    return [PSCustomObject]@{
        ExitCode = $code
        Output   = ($output -join "`n")
    }
}

function Get-FixtureHead() {
    return (& git -C $fixture rev-parse HEAD).Trim()
}

try {
    if (-not $fixture.StartsWith($docRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Fixture 路径必须位于项目内部: $fixture"
    }
    $null = [IO.Directory]::CreateDirectory($fixture)

    # 1. 复制受管文档与必要脚本基础设施
    $sourcePaths = @(& git -C $docRoot -c core.quotepath=false ls-files --cached --others --exclude-standard)
    if ($LASTEXITCODE -ne 0) { throw "无法检索源码文件列表" }

    foreach ($path in ($sourcePaths | Sort-Object -Unique)) {
        if ($path -match '(^|/)(\.test-work|build|dist)/|(^|/)(local\.properties|\.env)$|\.(jks|keystore|apk)$') { continue }
        # 排除真实 gradlew，使用 fixture 专用的 mock gradlew
        if ($path -eq 'gradlew' -or $path -eq 'gradlew.bat') { continue }
        $source = Join-Path $docRoot $path
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { continue }
        $target = Join-Path $fixture $path
        $null = [IO.Directory]::CreateDirectory((Split-Path $target))
        Copy-Item -LiteralPath $source -Destination $target
    }

    # 2. 安装 mock Gradle wrapper
    $mockScript = @'
param()
$behaviorFile = Join-Path $PSScriptRoot '.test-gradle-behavior'
$logFile = Join-Path $PSScriptRoot '.test-gradle-args.log'
[IO.File]::AppendAllText($logFile, ($args -join ' ') + "`n")
if (Test-Path -LiteralPath $behaviorFile) {
    $b = [IO.File]::ReadAllText($behaviorFile).Trim()
    if ($b -eq 'fail-test' -or $b -eq 'fail-lint') {
        exit 1
    }
    if ($b -eq 'mutate') {
        [IO.File]::AppendAllText((Join-Path $PSScriptRoot 'app/src/main/java/com/example/FooRenamed.kt'), "// mutate`n")
        exit 0
    }
}
exit 0
'@
    Write-Fixture '.test-gradlew.ps1' $mockScript

    $batContent = @"
@echo off
pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0.test-gradlew.ps1" %*
exit /b %ERRORLEVEL%
"@
    Write-Fixture 'gradlew.bat' $batContent

    $shContent = @"
#!/bin/sh
exec pwsh -NoProfile -File "`$PSScriptRoot/.test-gradlew.ps1" "`$@"
"@
    Write-Fixture 'gradlew' $shContent

    # 3. 创建基础源码文件 app/src/main/java/com/example/Foo.kt
    Write-Fixture 'app/src/main/java/com/example/Foo.kt' "package com.example`n`nclass Foo`n"

    # 4. 配置 pre-commit 钩子调用 Check-Local.ps1 -Mode Commit
    Write-Fixture '.githooks/pre-commit' "#!/bin/sh`nexec pwsh -NoProfile -File scripts/quality/Check-Local.ps1 -Mode Commit`n"

    # 5. 初始化 Git 仓库并提交初始基线
    Invoke-FixtureGit @('init', '--quiet')
    Invoke-FixtureGit @('add', '--all')
    $res0 = @(& git -C $fixture -c user.name=GateTester -c user.email=tester@example.invalid -c core.quotepath=false commit --no-verify -m "chore: initial baseline commit" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "初始基线提交失败: $($res0 -join "`n")"
    }
    Invoke-FixtureGit @('config', '--local', 'core.hooksPath', '.githooks')
    $initialHead = (Get-FixtureHead)

    # ================= 场景验证 =================

    # 场景 1: 纯文档提交跳过 Gradle
    Write-Host "测试场景 1: 纯文档提交跳过 Gradle..."
    $origTodo = [IO.File]::ReadAllText((Join-Path $fixture 'docs/todo.md'))
    Write-Fixture 'docs/todo.md' ($origTodo + "`n<!-- doc update -->`n")
    Invoke-FixtureGit @('add', '--', 'docs/todo.md')
    $logFile = Join-Path $fixture '.test-gradle-args.log'
    if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile }

    $res1 = Invoke-FixtureCommit "docs(todo): update documentation note"
    if ($res1.ExitCode -ne 0) {
        throw "纯文档提交失败: $($res1.Output)"
    }
    $head1 = Get-FixtureHead
    if ($head1 -eq $initialHead) { throw "纯文档提交 HEAD 未前进" }
    if (Test-Path -LiteralPath $logFile) {
        throw "纯文档提交不应调用 Gradle，但检测到了参数日志: $([IO.File]::ReadAllText($logFile))"
    }
    $passed++
    Write-Host "PASS: 场景 1 纯文档跳过 Gradle 成功"

    # 场景 2: 代码变更运行 Gradle 两项任务
    Write-Host "测试场景 2: 代码变更正常触发 Gradle 检查..."
    Write-Fixture 'app/src/main/java/com/example/Foo.kt' "package com.example`n`nclass Foo { val x = 1 }`n"
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/Foo.kt')
    if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile }

    $res2 = Invoke-FixtureCommit "feat(core): add property to Foo"
    if ($res2.ExitCode -ne 0) {
        throw "代码提交失败: $($res2.Output)"
    }
    $head2 = Get-FixtureHead
    if ($head2 -eq $head1) { throw "代码提交 HEAD 未前进" }
    if (-not (Test-Path -LiteralPath $logFile)) {
        throw "代码提交未调用 Gradle"
    }
    $gradleArgs = [IO.File]::ReadAllText($logFile)
    if ($gradleArgs -notmatch ':app:testDebugUnitTest' -or $gradleArgs -notmatch ':app:lintDebug') {
        throw "Gradle 调用参数未包含预期任务: $gradleArgs"
    }
    $passed++
    Write-Host "PASS: 场景 2 代码提交触发正确 Gradle 任务"

    # 场景 3: 单元测试失败阻断提交
    Write-Host "测试场景 3: 单元测试失败阻断提交..."
    Write-Fixture '.test-gradle-behavior' 'fail-test'
    Write-Fixture 'app/src/main/java/com/example/Foo.kt' "package com.example`n`nclass Foo { val x = 2 }`n"
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/Foo.kt')
    $res3 = Invoke-FixtureCommit "feat: should fail test"
    if ($res3.ExitCode -eq 0) {
        throw "预期测试失败阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $head2) { throw "失败提交不应让 HEAD 前进" }
    Remove-Item -LiteralPath (Join-Path $fixture '.test-gradle-behavior')
    $passed++
    Write-Host "PASS: 场景 3 测试失败成功阻断"

    # 场景 4: lint 失败阻断提交
    Write-Host "测试场景 4: lint 失败阻断提交..."
    Write-Fixture '.test-gradle-behavior' 'fail-lint'
    $res4 = Invoke-FixtureCommit "feat: should fail lint"
    if ($res4.ExitCode -eq 0) {
        throw "预期 lint 失败阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $head2) { throw "失败提交不应让 HEAD 前进" }
    Remove-Item -LiteralPath (Join-Path $fixture '.test-gradle-behavior')
    $passed++
    Write-Host "PASS: 场景 4 lint 失败成功阻断"

    # 恢复暂存区，使 HEAD 前进并进入后续场景
    $resRecover = Invoke-FixtureCommit "feat(core): update x to 2"
    if ($resRecover.ExitCode -ne 0) { throw "恢复后提交失败: $($resRecover.Output)" }
    $headRecover = Get-FixtureHead
    if ($headRecover -eq $head2) { throw "恢复提交 HEAD 未前进" }

    # 场景 5: 文档错误阻断提交
    Write-Host "测试场景 5: 文档链接错误阻断提交..."
    Write-Fixture 'docs/todo.md' ($origTodo + "`n[broken-link](missing-file-xyz.md)`n")
    Invoke-FixtureGit @('add', '--', 'docs/todo.md')
    $res5 = Invoke-FixtureCommit "docs: broken link"
    if ($res5.ExitCode -eq 0) {
        throw "预期文档断链阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $headRecover) { throw "文档错误不应让 HEAD 前进" }
    Write-Fixture 'docs/todo.md' $origTodo
    Invoke-FixtureGit @('add', '--', 'docs/todo.md')
    $passed++
    Write-Host "PASS: 场景 5 文档错误成功阻断"

    # 场景 6: 缺少 Gradle wrapper 阻断提交
    Write-Host "测试场景 6: 缺少 Gradle wrapper 阻断提交..."
    $gwBat = Join-Path $fixture 'gradlew.bat'
    $gwSh = Join-Path $fixture 'gradlew'
    $gwBackup = Join-Path $fixture 'gradlew.bat.bak'
    Move-Item -LiteralPath $gwBat -Destination $gwBackup
    Write-Fixture 'app/src/main/java/com/example/Foo.kt' "package com.example`n`nclass Foo { val x = 3 }`n"
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/Foo.kt')
    $res6 = Invoke-FixtureCommit "feat: missing gradlew"
    if ($res6.ExitCode -eq 0) {
        throw "预期缺少 wrapper 阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $headRecover) { throw "缺少 wrapper 不应让 HEAD 前进" }
    Move-Item -LiteralPath $gwBackup -Destination $gwBat
    $passed++
    Write-Host "PASS: 场景 6 缺少工具成功阻断"

    # 场景 7: 局部暂存不一致阻断提交 (工作区与暂存区不同)
    Write-Host "测试场景 7: 局部暂存不一致阻断提交..."
    # 暂存 x = 3
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/Foo.kt')
    # 在工作区修改为 x = 4，但不暂存
    Write-Fixture 'app/src/main/java/com/example/Foo.kt' "package com.example`n`nclass Foo { val x = 4 }`n"
    $res7 = Invoke-FixtureCommit "feat: partial staged"
    if ($res7.ExitCode -eq 0) {
        throw "预期工作区与暂存区不一致阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $headRecover) { throw "局部暂存不一致不应让 HEAD 前进" }
    # 暂存工作区修改，恢复一致
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/Foo.kt')
    $res7b = Invoke-FixtureCommit "feat(core): update x to 4"
    if ($res7b.ExitCode -ne 0) { throw "一致暂存提交失败: $($res7b.Output)" }
    $headRecover = Get-FixtureHead
    $passed++
    Write-Host "PASS: 场景 7 局部暂存不一致成功阻断"

    # 场景 8: 未跟踪源码阻断提交
    Write-Host "测试场景 8: 未跟踪代码文件阻断提交..."
    Write-Fixture 'app/src/main/java/com/example/Bar.kt' "package com.example`n`nclass Bar`n"
    Write-Fixture 'app/src/main/java/com/example/Foo.kt' "package com.example`n`nclass Foo { val x = 5 }`n"
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/Foo.kt')
    $res8 = Invoke-FixtureCommit "feat: untracked bar"
    if ($res8.ExitCode -eq 0) {
        throw "预期未跟踪源码阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $headRecover) { throw "未跟踪源码不应让 HEAD 前进" }
    Remove-Item -LiteralPath (Join-Path $fixture 'app/src/main/java/com/example/Bar.kt')
    $passed++
    Write-Host "PASS: 场景 8 未跟踪代码输入成功阻断"

    # 场景 9: 仅未跟踪 dist/ 不误阻断
    Write-Host "测试场景 9: 仅未跟踪 dist/ 目录产物不误阻断..."
    Write-Fixture 'dist/todo-v2.0.5-release.apk' "dummy apk bytes"
    $res9 = Invoke-FixtureCommit "feat(core): update x to 5 with untracked dist"
    if ($res9.ExitCode -ne 0) {
        throw "未跟踪 dist/ 产物导致误阻断: $($res9.Output)"
    }
    $headRecover = Get-FixtureHead
    $passed++
    Write-Host "PASS: 场景 9 未跟踪 dist/ 产物未误阻断"

    # 场景 10: 删除/重命名触发代码门禁
    Write-Host "测试场景 10: 代码重命名/删除正常触发门禁..."
    if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile }
    $fooPath = Join-Path $fixture 'app/src/main/java/com/example/Foo.kt'
    $renamedPath = Join-Path $fixture 'app/src/main/java/com/example/FooRenamed.kt'
    Move-Item -LiteralPath $fooPath -Destination $renamedPath
    Invoke-FixtureGit @('add', '--all')
    $res10 = Invoke-FixtureCommit "refactor: rename Foo to FooRenamed"
    if ($res10.ExitCode -ne 0) {
        throw "重命名提交失败: $($res10.Output)"
    }
    if (-not (Test-Path -LiteralPath $logFile)) {
        throw "代码重命名/删除未触发 Gradle 检查"
    }
    $headRecover = Get-FixtureHead
    $passed++
    Write-Host "PASS: 场景 10 代码重命名/删除触发门禁成功"

    # 场景 11: 中文与空格路径支持
    Write-Host "测试场景 11: 中文与空格路径支持..."
    Write-Fixture 'app/src/main/java/com/example/中文 测 试.kt' "package com.example`n`nclass 中文测试`n"
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/中文 测 试.kt')
    $res11 = Invoke-FixtureCommit "feat: add chinese file with spaces"
    if ($res11.ExitCode -ne 0) {
        throw "中文/空格路径提交失败: $($res11.Output)"
    }
    $headRecover = Get-FixtureHead
    $passed++
    Write-Host "PASS: 场景 11 中文与空格路径提交成功"

    # 场景 12: 检查期间工作区输入变动阻断
    Write-Host "测试场景 12: 检查期间输入被篡改阻断..."
    Write-Fixture '.test-gradle-behavior' 'mutate'
    Write-Fixture 'app/src/main/java/com/example/FooRenamed.kt' "package com.example`n`nclass FooRenamed { val y = 1 }`n"
    Invoke-FixtureGit @('add', '--', 'app/src/main/java/com/example/FooRenamed.kt')
    $res12 = Invoke-FixtureCommit "feat: mutate during check"
    if ($res12.ExitCode -eq 0) {
        throw "预期检查期间篡改输入阻断，但提交成功了！"
    }
    if ((Get-FixtureHead) -ne $headRecover) { throw "篡改不应让 HEAD 前进" }
    Remove-Item -LiteralPath (Join-Path $fixture '.test-gradle-behavior')
    Invoke-FixtureGit @('checkout', '--', 'app/src/main/java/com/example/FooRenamed.kt')
    $passed++
    Write-Host "PASS: 场景 12 检查期间输入变动成功阻断"

    # 场景 13: Full 模式验证
    Write-Host "测试场景 13: Check-Local.ps1 -Mode Full 验证..."
    $fullOutput = @(& pwsh -NoProfile -File (Join-Path $fixture 'scripts/quality/Check-Local.ps1') -Mode Full 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Full 模式执行失败: $($fullOutput -join "`n")"
    }
    $passed++
    Write-Host "PASS: 场景 13 Full 模式检查通过"

    # 场景 14: README 社区入口纯文档提交跳过 Gradle
    Write-Host "测试场景 14: README 纯文档提交跳过 Gradle..."
    # 场景 12 的阻断提交遗留了已暂存的代码变更（checkout -- 从暂存区恢复的仍是变异内容）；
    # 先把该文件重置回 HEAD 并同步工作区，保证本场景只提交 README
    Invoke-FixtureGit @('reset', '--quiet', '--', 'app/src/main/java/com/example/FooRenamed.kt')
    Invoke-FixtureGit @('checkout', '--quiet', 'HEAD', '--', 'app/src/main/java/com/example/FooRenamed.kt')
    $origReadme = [IO.File]::ReadAllText((Join-Path $fixture 'README.md'))
    Write-Fixture 'README.md' ($origReadme + "`n<!-- readme note update -->`n")
    Invoke-FixtureGit @('add', '--', 'README.md')
    if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile }
    $res14 = Invoke-FixtureCommit "docs(readme): update entry note"
    if ($res14.ExitCode -ne 0) {
        throw "README 纯文档提交失败: $($res14.Output)"
    }
    if ((Get-FixtureHead) -eq $headRecover) { throw "README 提交 HEAD 未前进" }
    if (Test-Path -LiteralPath $logFile) {
        throw "README 纯文档提交不应调用 Gradle，但检测到了参数日志: $([IO.File]::ReadAllText($logFile))"
    }
    $headRecover = Get-FixtureHead
    $passed++
    Write-Host "PASS: 场景 14 README 纯文档跳过 Gradle 成功"

    # 场景 15: CI 工作流变更触发代码门禁
    Write-Host "测试场景 15: CI 工作流变更触发 Gradle 门禁..."
    $origCi = [IO.File]::ReadAllText((Join-Path $fixture '.github/workflows/ci.yml'))
    Write-Fixture '.github/workflows/ci.yml' ($origCi + "`n# gate probe comment`n")
    Invoke-FixtureGit @('add', '--', '.github/workflows/ci.yml')
    if (Test-Path -LiteralPath $logFile) { Remove-Item -LiteralPath $logFile }
    $res15 = Invoke-FixtureCommit "ci: probe workflow change triggers gate"
    if ($res15.ExitCode -ne 0) {
        throw "工作流变更提交失败: $($res15.Output)"
    }
    if (-not (Test-Path -LiteralPath $logFile)) {
        throw "工作流变更未触发 Gradle 门禁"
    }
    $headRecover = Get-FixtureHead
    $passed++
    Write-Host "PASS: 场景 15 工作流变更触发门禁成功"

    Write-Host "`nPASS: 全部 $passed 项本地门禁隔离回归场景验证通过。"
} finally {
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    if (-not $resolvedFixture.StartsWith($testParent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not $testParent.StartsWith($docRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "拒绝清理非测试目录: $resolvedFixture"
    }
    if (Test-Path -LiteralPath $resolvedFixture) {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
    if ((Test-Path -LiteralPath $testParent) -and @(Get-ChildItem -LiteralPath $testParent -Force).Count -eq 0) {
        Remove-Item -LiteralPath $testParent
    }
}
