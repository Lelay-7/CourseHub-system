$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$schemaPath = Join-Path $root 'backend\src\main\resources\db\init.sql'
$mapperDir = Join-Path $root 'backend\src\main\resources\mapper'
$schema = Get-Content -LiteralPath $schemaPath -Raw

$createdTables = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
[regex]::Matches($schema, '(?im)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-z_]+)`?') |
    ForEach-Object { [void]$createdTables.Add($_.Groups[1].Value) }

$usedTables = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
Get-ChildItem -LiteralPath $mapperDir -Filter '*.xml' | ForEach-Object {
    $content = Get-Content -LiteralPath $_.FullName -Raw
    $sql = [regex]::Replace($content, '<[^>]+>', ' ')
    [regex]::Matches($sql, '(?im)\b(?:FROM|JOIN|UPDATE|INTO)\s+`?([a-z_]+)`?') |
        ForEach-Object { [void]$usedTables.Add($_.Groups[1].Value) }
}

$missingTables = @($usedTables | Where-Object { -not $createdTables.Contains($_) } | Sort-Object)
if ($missingTables.Count -gt 0) {
    throw "init.sql is missing mapper tables: $($missingTables -join ', ')"
}

$requiredPatterns = [ordered]@{
    'user.avatar_storage_key' = '(?is)CREATE\s+TABLE[^;]+\buser\b[^;]+\bavatar_storage_key\b'
    'course_review.is_anonymous' = '(?is)CREATE\s+TABLE[^;]+\bcourse_review\b[^;]+\bis_anonymous\b'
    'course_review.reward_granted' = '(?is)CREATE\s+TABLE[^;]+\bcourse_review\b[^;]+\breward_granted\b'
    'material.storage_key' = '(?is)CREATE\s+TABLE[^;]+\bmaterial\b[^;]+\bstorage_key\b'
    'material.original_file_name' = '(?is)CREATE\s+TABLE[^;]+\bmaterial\b[^;]+\boriginal_file_name\b'
    'material.content_type' = '(?is)CREATE\s+TABLE[^;]+\bmaterial\b[^;]+\bcontent_type\b'
    'material_download uniqueness' = '(?is)CREATE\s+TABLE[^;]+\bmaterial_download\b[^;]+UNIQUE\s+KEY[^;]+material_id\s*,\s*user_id'
}

foreach ($entry in $requiredPatterns.GetEnumerator()) {
    if ($schema -notmatch $entry.Value) {
        throw "init.sql is missing contract: $($entry.Key)"
    }
}

Write-Host "Schema contract passed for $($usedTables.Count) mapper tables."
