param(
    [string]$MysqlPath = 'mysql.exe',
    [string]$RootUser = 'root',
    [string]$RootPassword = $env:MYSQL_PWD
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$initSql = Join-Path $root 'backend\src\main\resources\db\init.sql'
$database = 'bgss_xtt_init_check_' + (Get-Date -Format 'yyyyMMddHHmmss')
$previousPassword = $env:MYSQL_PWD
$databaseCreated = $false

if ([string]::IsNullOrWhiteSpace($RootPassword)) {
    throw 'Set MYSQL_PWD or pass -RootPassword before running this validation.'
}

$mysql = (Get-Command $MysqlPath -ErrorAction Stop).Source
$utf8 = New-Object System.Text.UTF8Encoding($false)
$OutputEncoding = $utf8
[Console]::OutputEncoding = $utf8

try {
    $env:MYSQL_PWD = $RootPassword
    $sql = (Get-Content -LiteralPath $initSql -Raw -Encoding utf8).Replace('bgss_xtt', $database)
    $sql | & $mysql --default-character-set=utf8mb4 "-u$RootUser"
    if ($LASTEXITCODE -ne 0) {
        throw "init.sql import failed with exit code $LASTEXITCODE"
    }
    $databaseCreated = $true

    $query = @'
SELECT 'users', COUNT(*) FROM {0}.user
UNION ALL SELECT 'categories', COUNT(*) FROM {0}.category
UNION ALL SELECT 'courses', COUNT(*) FROM {0}.course
UNION ALL SELECT 'materials', COUNT(*) FROM {0}.material
UNION ALL SELECT 'reports', COUNT(*) FROM {0}.material_report
UNION ALL SELECT 'orders', COUNT(*) FROM {0}.`order`
UNION ALL SELECT 'free_materials', COUNT(*) FROM {0}.material WHERE status = 1 AND download_score = 0
UNION ALL SELECT 'score_materials', COUNT(*) FROM {0}.material WHERE status = 1 AND download_score > 0
UNION ALL SELECT 'weekend_courses', COUNT(*) FROM {0}.course
    WHERE status = 1
      AND (HEX(schedule) LIKE '%E591A8E585AD%' OR HEX(schedule) LIKE '%E591A8E697A5%')
UNION ALL SELECT 'orphan_course_category', COUNT(*) FROM {0}.course c
    LEFT JOIN {0}.category k ON k.category_id = c.category_id
    WHERE k.category_id IS NULL
UNION ALL SELECT 'invalid_course_teacher', COUNT(*) FROM {0}.course c
    LEFT JOIN {0}.user u ON u.user_id = c.teacher_id AND u.user_type = 2
    WHERE c.teacher_id IS NOT NULL AND u.user_id IS NULL
UNION ALL SELECT 'orphan_material_course', COUNT(*) FROM {0}.material m
    LEFT JOIN {0}.course c ON c.course_id = m.course_id
    WHERE m.course_id IS NOT NULL AND c.course_id IS NULL
UNION ALL SELECT 'orphan_material_uploader', COUNT(*) FROM {0}.material m
    LEFT JOIN {0}.user u ON u.user_id = m.uploader_id
    WHERE u.user_id IS NULL
UNION ALL SELECT 'orphan_selection', COUNT(*) FROM {0}.course_selection s
    LEFT JOIN {0}.course c ON c.course_id = s.course_id
    LEFT JOIN {0}.user u ON u.user_id = s.user_id
    WHERE c.course_id IS NULL OR u.user_id IS NULL
UNION ALL SELECT 'orphan_review', COUNT(*) FROM {0}.course_review r
    LEFT JOIN {0}.course c ON c.course_id = r.course_id
    LEFT JOIN {0}.user u ON u.user_id = r.user_id
    WHERE c.course_id IS NULL OR u.user_id IS NULL
UNION ALL SELECT 'orphan_download', COUNT(*) FROM {0}.material_download d
    LEFT JOIN {0}.material m ON m.material_id = d.material_id
    LEFT JOIN {0}.user u ON u.user_id = d.user_id
    WHERE m.material_id IS NULL OR u.user_id IS NULL
UNION ALL SELECT 'orphan_report', COUNT(*) FROM {0}.material_report r
    LEFT JOIN {0}.material m ON m.material_id = r.material_id
    LEFT JOIN {0}.user u ON u.user_id = r.reporter_id
    WHERE m.material_id IS NULL OR u.user_id IS NULL
UNION ALL SELECT 'orphan_favorite', COUNT(*) FROM {0}.favorite f
    LEFT JOIN {0}.course c ON f.target_type = 1 AND c.course_id = f.target_id
    LEFT JOIN {0}.material m ON f.target_type = 2 AND m.material_id = f.target_id
    WHERE (f.target_type = 1 AND c.course_id IS NULL)
       OR (f.target_type = 2 AND m.material_id IS NULL)
UNION ALL SELECT 'orphan_order', COUNT(*) FROM {0}.`order` o
    LEFT JOIN {0}.user u ON u.user_id = o.user_id
    LEFT JOIN {0}.goods g ON g.goods_id = o.goods_id
    WHERE u.user_id IS NULL OR g.goods_id IS NULL
UNION ALL SELECT 'orphan_square_comment', COUNT(*) FROM {0}.square_comment c
    LEFT JOIN {0}.square_post p ON p.post_id = c.post_id
    LEFT JOIN {0}.user u ON u.user_id = c.user_id
    WHERE p.post_id IS NULL OR u.user_id IS NULL
UNION ALL SELECT 'orphan_square_like', COUNT(*) FROM {0}.square_like l
    LEFT JOIN {0}.square_post p ON p.post_id = l.post_id
    LEFT JOIN {0}.user u ON u.user_id = l.user_id
    WHERE p.post_id IS NULL OR u.user_id IS NULL;
'@ -f $database

    $rows = & $mysql --default-character-set=utf8mb4 "-u$RootUser" --batch --skip-column-names -e $query
    if ($LASTEXITCODE -ne 0) {
        throw "validation query failed with exit code $LASTEXITCODE"
    }

    $metrics = @{}
    foreach ($row in $rows) {
        $name, $value = $row -split "`t", 2
        $metrics[$name] = [int]$value
        Write-Host "$name=$value"
    }

    foreach ($name in @('users', 'categories', 'courses', 'materials', 'reports', 'orders',
            'free_materials', 'score_materials', 'weekend_courses')) {
        if ($metrics[$name] -lt 1) {
            throw "Expected $name to contain validation data."
        }
    }
    foreach ($name in @(
            'orphan_course_category', 'invalid_course_teacher', 'orphan_material_course',
            'orphan_material_uploader', 'orphan_selection', 'orphan_review', 'orphan_download',
            'orphan_report', 'orphan_favorite', 'orphan_order', 'orphan_square_comment',
            'orphan_square_like')) {
        if ($metrics[$name] -ne 0) {
            throw "Validation failed: $name=$($metrics[$name])."
        }
    }

    Write-Host 'init.sql validation passed.'
}
finally {
    if ($databaseCreated) {
        & $mysql "-u$RootUser" -e "DROP DATABASE IF EXISTS $database" | Out-Null
    }
    $env:MYSQL_PWD = $previousPassword
}
