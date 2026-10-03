#Requires -Version 7.0
[CmdletBinding()]
param(
    [ValidateSet('Check', 'Sync')][string]$Mode = 'Check',
    [switch]$Staged
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$docRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$issues = [Collections.Generic.List[string]]::new()
$documents = [ordered]@{}
$utf8 = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding = $utf8
# Maintainer decision: the root README.md is the product entry page and is NOT governed
# Markdown (no metadata/link/index checks). It is skipped entirely by the scope check.
# LICENSE is a standard license text and likewise not governed Markdown.

function Add-Issue([string]$rule, [string]$location, [string]$message) {
    $issues.Add("${rule} ${location}: $message")
}

function Invoke-DocGit([string[]]$Arguments) {
    $result = @(& git -C $docRoot -c core.quotepath=false @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "DOC-SOURCE git: $($result -join ' ')" }
    return $result
}

function Read-Source([string]$relativePath) {
    if ($Staged) {
        return (((Invoke-DocGit @('show', ":$relativePath")) -join "`n").Replace("`r`n", "`n").Replace("`r", "")) + "`n"
    }
    $absolutePath = Join-Path $docRoot $relativePath
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
        throw "DOC-SOURCE ${relativePath}: required file missing. Restore it or update the generator."
    }
    return [IO.File]::ReadAllText($absolutePath).Replace("`r`n", "`n")
}

function Remove-CodeBlocks([string]$value) {
    $fence = ''
    $lines = foreach ($line in ($value -split "`n")) {
        if ($line -match '^ {0,3}(`{3,}|~{3,})') {
            $marker = $Matches[1]
            if ($fence -eq '') { $fence = $marker }
            elseif ($marker[0] -eq $fence[0] -and $marker.Length -ge $fence.Length) { $fence = '' }
            ''
        } elseif ($fence -ne '') { '' } else { $line }
    }
    return $lines -join "`n"
}

function Get-UniqueValue([string]$value, [string]$pattern, [string]$source) {
    $found = [regex]::Matches($value, $pattern)
    if ($found.Count -ne 1) {
        throw "DOC-SOURCE ${source}: expected one match for $pattern; update parser after checking source."
    }
    return $found[0].Groups[1].Value
}

function Set-GeneratedBlock([string]$path, [string]$name, [string]$content) {
    if (-not $documents.Contains($path)) { throw "DOC-SOURCE ${path}: required document missing." }
    $value = $documents[$path].Text
    $start = "<!-- docs:${name}:start -->"
    $end = "<!-- docs:${name}:end -->"
    $pattern = '(?s)' + [regex]::Escape($start) + '(.*?)' + [regex]::Escape($end)
    $found = [regex]::Matches($value, $pattern)
    if ($found.Count -ne 1 -or [regex]::Matches($value, [regex]::Escape($start)).Count -ne 1 -or
        [regex]::Matches($value, [regex]::Escape($end)).Count -ne 1) {
        Add-Issue 'DOC-GENERATED' $path "Keep exactly one complete docs:$name marker pair."
        return
    }
    $expected = "$start`n$content`n$end"
    if ($found[0].Value -ceq $expected) { return }
    if ($Mode -eq 'Check') {
        Add-Issue 'DOC-GENERATED' $path 'Generated content is stale. Run -Mode Sync, review and stage the result.'
    } else {
        $replacement = $value.Substring(0, $found[0].Index) + $expected +
            $value.Substring($found[0].Index + $found[0].Length)
        [IO.File]::WriteAllText((Join-Path $docRoot $path), $replacement, $utf8)
        $documents[$path].Text = $replacement
        Write-Host "SYNC $path ($name)"
    }
}

function Get-HeadingIds([string]$value) {
    $ids = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $counts = @{}
    foreach ($match in [regex]::Matches((Remove-CodeBlocks $value), '(?m)^#{1,6}\s+(.+?)\s*#*\s*$')) {
        $slug = $match.Groups[1].Value.ToLowerInvariant().Replace('`', '').Replace('*', '')
        $slug = [regex]::Replace($slug, '[^\p{L}\p{N}_\-\s]', '')
        $slug = [regex]::Replace($slug.Trim(), '\s', '-')
        $number = if ($counts.ContainsKey($slug)) { $counts[$slug] + 1 } else { 0 }
        $counts[$slug] = $number
        $null = $ids.Add($(if ($number -gt 0) { "$slug-$number" } else { $slug }))
    }
    return ,$ids
}

try {
    if ($Staged -and $Mode -eq 'Sync') { throw 'DOC-SOURCE: Sync cannot modify the staged snapshot. Use Check -Staged.' }
    $pathArgs = @('ls-files', '--cached')
    if (-not $Staged) { $pathArgs += @('--others', '--exclude-standard') }
    $paths = @(Invoke-DocGit $pathArgs | Sort-Object -Unique)
    if (-not $Staged) {
        $paths = @($paths | Where-Object { Test-Path -LiteralPath (Join-Path $docRoot $_) -PathType Leaf })
    }
    $docExtensions = '\.(md|markdown|mdx|rst|adoc|txt|org)$'
    foreach ($managedRoot in @('docs', '.agents/skills')) {
        if ($Staged -or -not (Test-Path -LiteralPath (Join-Path $docRoot $managedRoot))) { continue }
        foreach ($file in Get-ChildItem -LiteralPath (Join-Path $docRoot $managedRoot) -File -Recurse -Force) {
            $relative = [IO.Path]::GetRelativePath($docRoot, $file.FullName).Replace('\', '/')
            if ($relative -match $docExtensions -and $relative -notin $paths) {
                Add-Issue 'DOC-SCOPE' $relative 'Formal documentation is ignored by Git. Remove the ignore rule.'
            }
        }
    }
    foreach ($path in $paths) {
        if ($path -notmatch $docExtensions) { continue }
        $isSkill = $path -cmatch '^\.agents/skills/([a-z0-9]+(?:-[a-z0-9]+)*)/SKILL\.md$'
        $skillName = if ($isSkill) { $Matches[1] } else { '' }
        if ($path -ceq 'README.md') { continue }
        if (-not $isSkill -and $path -cne 'AGENTS.md' -and $path -cnotmatch '^docs/.+\.md$') {
            Add-Issue 'DOC-SCOPE' $path 'Move permanent knowledge to docs/*.md; explicitly manage temporary/generated inputs.'
            continue
        }
        $value = Read-Source $path
        $metadata = @{}
        $skillFields = @{}
        $front = [regex]::Match($value, '\A---\n(.*?)\n---\n', 'Singleline')
        if ($front.Success) {
            $inSkillMetadata = $false
            foreach ($line in ($front.Groups[1].Value -split "`n")) {
                if ($isSkill) {
                    if ($line -cmatch '^(name|description):\s*(\S.*)$' -and -not $inSkillMetadata) {
                        if ($skillFields.ContainsKey($Matches[1])) { Add-Issue 'DOC-META' $path 'Duplicate skill field.' }
                        $skillFields[$Matches[1]] = $Matches[2].Trim()
                        continue
                    }
                    if ($line -ceq 'metadata:' -and -not $inSkillMetadata) { $inSkillMetadata = $true; continue }
                    if (-not $inSkillMetadata -or $line -cnotmatch '^  [a-z]+:\s*\S') {
                        Add-Issue 'DOC-META' $path 'Skills require name/description followed by metadata with two-space flat fields.'
                        continue
                    }
                    $line = $line.Substring(2)
                }
                if ($line -match '^([a-z]+):\s*(.+)$') {
                    if ($metadata.ContainsKey($Matches[1])) { Add-Issue 'DOC-META' $path 'Duplicate metadata key.' }
                    $metadata[$Matches[1]] = $Matches[2].Trim()
                } else { Add-Issue 'DOC-META' $path 'Metadata must use flat key: value lines.' }
            }
        }
        if ($isSkill) {
            if ($skillFields['name'] -cne $skillName -or $skillName.Length -gt 64) {
                Add-Issue 'DOC-META' $path 'Skill name must match its folder and be at most 64 characters.'
            }
            if ([string]::IsNullOrWhiteSpace($skillFields['description']) -or
                $skillFields['description'].Length -gt 1024 -or $skillFields['description'] -match '[<>]') {
                Add-Issue 'DOC-META' $path 'Skill description must be plain text of 1-1024 characters without angle brackets.'
            }
        }
        foreach ($key in @('title', 'purpose', 'status', 'owner', 'scope', 'updated', 'verification', 'verified')) {
            if (-not $metadata.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($metadata[$key])) {
                Add-Issue 'DOC-META' $path "Missing $key. Add a truthful value."
            }
        }
        if ($metadata['status'] -notin @('草案', '当前有效', '已取代', '历史归档')) {
            Add-Issue 'DOC-META' $path 'Invalid lifecycle status.'
        }
        if ($metadata['verification'] -notin @('未验证', '已静态检查', '已在指定环境验证')) {
            Add-Issue 'DOC-META' $path 'Invalid verification status.'
        }
        $date = [datetime]::MinValue
        if (-not [datetime]::TryParseExact($metadata['updated'], 'yyyy-MM-dd',
                [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::None, [ref]$date)) {
            Add-Issue 'DOC-META' $path 'updated must be a valid YYYY-MM-DD date.'
        }
        foreach ($key in @('title', 'purpose')) {
            if ($metadata[$key] -match '[\[\]|<>]') { Add-Issue 'DOC-META' $path "$key must be plain text without table/link markup." }
        }
        if ([regex]::Matches((Remove-CodeBlocks $value), '(?m)^# [^\n]+$').Count -ne 1) {
            Add-Issue 'DOC-META' $path 'Use exactly one H1 outside code blocks.'
        }
        $documents[$path] = [pscustomobject]@{ Text = $value; Metadata = $metadata }
    }
    if ($issues.Count -gt 0) { throw 'DOC-SOURCE: fix inventory/metadata before generating or validating links.' }
    # docs/archive/index.md is required and generated only when at least one archived
    # document exists; keeping a pre-built empty index is rejected (see docs/documentation.md).
    $archiveDocPaths = @($documents.Keys | Where-Object { $_ -cmatch '^docs/archive/(.+)$' -and $_ -cne 'docs/archive/index.md' })
    $hasArchiveDocs = $archiveDocPaths.Count -gt 0
    if ($hasArchiveDocs -and -not $documents.Contains('docs/archive/index.md')) {
        throw 'DOC-SOURCE docs/archive/index.md: required document missing (archive documents exist).'
    }
    foreach ($required in @('AGENTS.md', 'docs/index.md', 'docs/development.md')) {
        if (-not $documents.Contains($required)) { throw "DOC-SOURCE ${required}: required document missing." }
    }

    $indexRows = @('| 文档 | 用途 | 生命周期 | 验证状态 |', '| --- | --- | --- | --- |')
    foreach ($path in $documents.Keys) {
        if ($path -cmatch '^\.agents/skills/') { continue }
        if ($path -cmatch '^docs/archive/' -and $path -cne 'docs/archive/index.md') { continue }
        $meta = $documents[$path].Metadata
        $link = [IO.Path]::GetRelativePath((Join-Path $docRoot 'docs'), (Join-Path $docRoot $path)).Replace('\', '/')
        $link = ($link.Split('/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
        $indexRows += "| [$($meta['title'])]($link) | $($meta['purpose']) | $($meta['status']) | $($meta['verification']) |"
    }
    Set-GeneratedBlock 'docs/index.md' 'index' ($indexRows -join "`n")

    if ($hasArchiveDocs) {
        $archiveRows = @('| 归档文档 | 用途 | 生命周期 | 验证状态 |', '| --- | --- | --- | --- |')
        foreach ($path in $archiveDocPaths) {
            $meta = $documents[$path].Metadata
            $fileName = $path -creplace '^docs/archive/', ''
            $link = ($fileName.Split('/') | ForEach-Object { [Uri]::EscapeDataString($_) }) -join '/'
            $archiveRows += "| [$($meta['title'])]($link) | $($meta['purpose']) | $($meta['status']) | $($meta['verification']) |"
        }
        Set-GeneratedBlock 'docs/archive/index.md' 'archive' ($archiveRows -join "`n")
    } elseif ($documents.Contains('docs/archive/index.md')) {
        Add-Issue 'DOC-SCOPE' 'docs/archive/index.md' 'Archive index exists without archive documents; remove the pre-built empty index.'
    }

    $appConfig = Read-Source 'app/build.gradle.kts'
    $versions = Read-Source 'gradle/libs.versions.toml'
    $wrapper = Read-Source 'gradle/wrapper/gradle-wrapper.properties'
    $database = Read-Source 'app/src/main/java/com/example/data/AppDatabase.kt'
    $facts = [ordered]@{}
    foreach ($key in @('versionName', 'applicationId', 'namespace')) {
        $facts[$key] = Get-UniqueValue $appConfig "(?m)^\s*$key\s*=\s*`"([^`"]+)`"" 'app/build.gradle.kts'
    }
    foreach ($key in @('versionCode', 'minSdk', 'targetSdk')) {
        $facts[$key] = Get-UniqueValue $appConfig "(?m)^\s*$key\s*=\s*(\d+)" 'app/build.gradle.kts'
    }
    $facts['compileSdk API'] = Get-UniqueValue $appConfig 'compileSdk\s*\{\s*version\s*=\s*release\((\d+)\)' 'app/build.gradle.kts'
    $facts['compileSdk minor'] = Get-UniqueValue $appConfig 'minorApiLevel\s*=\s*(\d+)' 'app/build.gradle.kts'
    foreach ($key in @('sourceCompatibility', 'targetCompatibility')) {
        $facts["Java $key"] = Get-UniqueValue $appConfig "$key\s*=\s*JavaVersion\.VERSION_(\d+)" 'app/build.gradle.kts'
    }
    $facts['Gradle Wrapper'] = Get-UniqueValue $wrapper 'distributionUrl=.*gradle-([\d.]+)-bin\.zip' 'gradle/wrapper/gradle-wrapper.properties'
    foreach ($key in @('agp', 'kotlin', 'googleDevtoolsKsp', 'composeBom', 'roomRuntime', 'roomKtx', 'roomCompiler',
            'reorderable', 'kotlinxCoroutinesAndroid', 'kotlinxCoroutinesCore', 'navigationCompose')) {
        $facts["版本目录 $key"] = Get-UniqueValue $versions "(?m)^$key\s*=\s*`"([^`"]+)`"" 'gradle/libs.versions.toml'
    }
    $facts['Room schema'] = Get-UniqueValue $database '@Database\([^\r\n]*version\s*=\s*(\d+)' 'AppDatabase.kt'
    $configRows = @('| 配置项 | 当前声明值 |', '| --- | --- |')
    foreach ($key in $facts.Keys) { $configRows += '| ' + $key + ' | `' + $facts[$key] + '` |' }
    Set-GeneratedBlock 'docs/development.md' 'config' ($configRows -join "`n")

    $edges = @{}
    $linkCount = 0
    foreach ($path in $documents.Keys) {
        $edges[$path] = [Collections.Generic.List[string]]::new()
        $value = Remove-CodeBlocks $documents[$path].Text
        $value = [regex]::Replace($value, '`[^`\n]+`', '')
        if ($value -match '\[[^\]\n]+\]\s*\[[^\]\n]*\]|(?m)^\s*\[[^\]]+\]:|<a\s|<img\s') {
            Add-Issue 'DOC-LINK' $path 'Use inline Markdown links; reference-style/HTML links are unsupported.'
        }
        foreach ($match in [regex]::Matches($value, '!?\[[^\]\n]*\]\((?<target><[^>\n]+>|[^\s)]+)(?:\s+"[^"]*")?\)')) {
            $target = $match.Groups['target'].Value.Trim('<', '>')
            if ($target -match '^(https?://|mailto:)') { continue }
            $lineNumber = ($value.Substring(0, $match.Index) -split "`n").Count
            $location = "${path}:$lineNumber"
            $parts = $target -split '#', 2
            $linkPath = [Uri]::UnescapeDataString($parts[0])
            if ($linkPath -match '^(?:[a-z][a-z0-9+.-]*:|[/\\])') {
                Add-Issue 'DOC-LINK' $location 'Use relative paths within this project.'
                continue
            }
            $absolute = if ($linkPath -eq '') { Join-Path $docRoot $path } else {
                [IO.Path]::GetFullPath((Join-Path (Split-Path (Join-Path $docRoot $path)) $linkPath))
            }
            if (-not $absolute.StartsWith($docRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
                Add-Issue 'DOC-LINK' $location 'Link escapes project root.'
                continue
            }
            $relative = [IO.Path]::GetRelativePath($docRoot, $absolute).Replace('\', '/')
            if ($relative -cmatch '^\.agents/skills(?:/|$)') {
                Add-Issue 'DOC-LINK' $location 'Managed Markdown must not link to Skills; standard-path Skills are injected automatically.'
                continue
            }
            $exists = if ($Staged) { $relative -cin $paths -or @($paths | Where-Object { $_.StartsWith("$relative/", [StringComparison]::Ordinal) }).Count -gt 0 }
                else { Test-Path -LiteralPath $absolute }
            if (-not $exists) { Add-Issue 'DOC-LINK' $location "Missing target: $relative. Update or remove the link."; continue }
            $linkCount++
            if ($documents.Contains($relative)) {
                $edges[$path].Add($relative)
                if ($parts.Count -gt 1 -and $parts[1] -ne '') {
                    $anchor = [Uri]::UnescapeDataString($parts[1])
                    if (-not (Get-HeadingIds $documents[$relative].Text).Contains($anchor)) {
                        Add-Issue 'DOC-LINK' $location "Missing heading: $relative#$anchor."
                    }
                }
            } elseif ($parts.Count -gt 1 -and $parts[1] -ne '') {
                Add-Issue 'DOC-LINK' $location 'Local fragments are supported only for Markdown document headings.'
            }
        }
    }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $queue = [Collections.Generic.Queue[string]]::new()
    $queue.Enqueue('AGENTS.md')
    while ($queue.Count -gt 0) {
        $path = $queue.Dequeue()
        if (-not $seen.Add($path)) { continue }
        foreach ($next in $edges[$path]) { $queue.Enqueue($next) }
    }
    foreach ($path in $documents.Keys) {
        if ($path -cmatch '^\.agents/skills/') { continue }
        if (-not $seen.Contains($path)) { Add-Issue 'DOC-REACHABLE' $path 'Unreachable from AGENTS.md. Repair entry/index links.' }
    }
    if ($issues.Count -gt 0) { throw 'Documentation validation failed.' }
    $snapshot = if ($Staged) { 'staged' } else { 'working-tree' }
    $generatedBlocks = if ($hasArchiveDocs) { 3 } else { 2 }
    Write-Host "PASS: documents=$($documents.Count), localLinks=$linkCount, generatedBlocks=$generatedBlocks, snapshot=$snapshot"
    exit 0
} catch {
    foreach ($issue in $issues) { Write-Host $issue }
    Write-Host $_.Exception.Message
    exit 1
}
