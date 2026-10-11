$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$schemaPath = Join-Path $root 'backend\src\main\resources\db\init.sql'
$migrationDir = Join-Path $root 'backend\src\main\resources\db\migrations'
$mapperDir = Join-Path $root 'backend\src\main\resources\mapper'
$schemaFiles = @($schemaPath) + @(Get-ChildItem -LiteralPath $migrationDir -Filter '*.sql' -File |
    Sort-Object Name | ForEach-Object { $_.FullName })
$schema = ($schemaFiles | ForEach-Object { Get-Content -LiteralPath $_ -Raw -Encoding UTF8 }) -join "`n"

$createdTables = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
[regex]::Matches($schema, '(?im)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-z_]+)`?') |
    ForEach-Object { [void]$createdTables.Add($_.Groups[1].Value) }

$usedTables = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
Get-ChildItem -LiteralPath $mapperDir -Filter '*.xml' | ForEach-Object {
    $content = Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
    $sql = [regex]::Replace($content, '<[^>]+>', ' ')
    $sql = [regex]::Replace($sql, '(?im)\bFOR\s+UPDATE\b|\bON\s+DUPLICATE\s+KEY\s+UPDATE\b', ' ')
    [regex]::Matches($sql, '(?im)\b(?:FROM|JOIN|UPDATE|INTO)\s+`?([a-z_]+)`?') |
        ForEach-Object { [void]$usedTables.Add($_.Groups[1].Value) }
}

$missingTables = @($usedTables | Where-Object { -not $createdTables.Contains($_) } | Sort-Object)
if ($missingTables.Count -gt 0) {
    throw "init.sql and migration chain are missing mapper tables: $($missingTables -join ', ')"
}

$requiredPatterns = [ordered]@{
    'user.avatar_storage_key' = '(?is)CREATE\s+TABLE[^;]+\buser\b[^;]+\bavatar_storage_key\b'
    'course_review.is_anonymous' = '(?is)CREATE\s+TABLE[^;]+\bcourse_review\b[^;]+\bis_anonymous\b'
    'course_review.reward_granted' = '(?is)CREATE\s+TABLE[^;]+\bcourse_review\b[^;]+\breward_granted\b'
    'material.storage_key' = '(?is)CREATE\s+TABLE[^;]+\bmaterial\b[^;]+\bstorage_key\b'
    'material.original_file_name' = '(?is)CREATE\s+TABLE[^;]+\bmaterial\b[^;]+\boriginal_file_name\b'
    'material.content_type' = '(?is)CREATE\s+TABLE[^;]+\bmaterial\b[^;]+\bcontent_type\b'
    'material_download uniqueness' = '(?is)CREATE\s+TABLE[^;]+\bmaterial_download\b[^;]+UNIQUE\s+KEY[^;]+material_id\s*,\s*user_id'
    'user.recovery_email' = '(?is)ALTER\s+TABLE\s+`?user`?\s+ADD\s+COLUMN\s+`?recovery_email`?\b'
    'user.recovery_email_verified_at' = '(?is)ALTER\s+TABLE\s+`?user`?\s+ADD\s+COLUMN\s+`?recovery_email_verified_at`?\b'
    'user.credential_version' = '(?is)ALTER\s+TABLE\s+`?user`?\s+ADD\s+COLUMN\s+`?credential_version`?\b'
    'friend canonical pair' = '(?is)CREATE\s+TABLE[^;]+\bfriend\b[^;]+GENERATED\s+ALWAYS\s+AS\s*\(LEAST[^;]+UNIQUE\s+KEY\s+uk_friend_pair\s*\(user_low_id\s*,\s*user_high_id\)'
    'chat private uniqueness' = '(?is)CREATE\s+TABLE[^;]+\bchat_conversation\b[^;]+UNIQUE\s+KEY\s+uk_chat_private_pair\s*\(conversation_type\s*,\s*private_user_low_id\s*,\s*private_user_high_id\)'
    'chat member key' = '(?is)CREATE\s+TABLE[^;]+\bchat_conversation_member\b[^;]+PRIMARY\s+KEY\s*\(conversation_id\s*,\s*user_id\)'
    'chat sequence uniqueness' = '(?is)CREATE\s+TABLE[^;]+\bchat_message\b[^;]+UNIQUE\s+KEY\s+uk_chat_sequence\s*\(conversation_id\s*,\s*sequence\)'
    'chat retry uniqueness' = '(?is)CREATE\s+TABLE[^;]+\bchat_message\b[^;]+UNIQUE\s+KEY\s+uk_chat_client\s*\(sender_id\s*,\s*client_message_id\)'
    'chat quota key' = '(?is)CREATE\s+TABLE[^;]+\bchat_send_rate_window\b[^;]+PRIMARY\s+KEY\s*\(user_id\s*,\s*window_start\s*,\s*window_seconds\)'
}

foreach ($entry in $requiredPatterns.GetEnumerator()) {
    if ($schema -notmatch $entry.Value) {
        throw "init.sql and migration chain are missing contract: $($entry.Key)"
    }
}

Write-Host "Schema contract passed for $($usedTables.Count) mapper tables across $($schemaFiles.Count) SQL files."
