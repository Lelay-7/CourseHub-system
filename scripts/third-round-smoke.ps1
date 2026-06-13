param(
    [string]$MysqlPassword
)

$ErrorActionPreference = 'Stop'

if ($MysqlPassword) {
    $env:MYSQL_PWD = $MysqlPassword
} elseif ($env:MYSQL_PWD) {
    # use existing environment variable
} else {
    throw 'MySQL root password is required. Pass -MysqlPassword or set $env:MYSQL_PWD before running this script.'
}

$baseUrl = 'http://localhost:8080'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$root = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $PSScriptRoot 'crud-smoke-upload.txt'
$stamp = Get-Date -Format 'MMddHHmmss'
$username1 = "third${stamp}a"
$username2 = "third${stamp}b"
$password = 'Smoke123'
$courseCode = "T$stamp"
$courseName = "Third Round $stamp"
$materialName = "Third Material $stamp"
$postContent = "Third post $stamp"
$feedbackType = -join ([char[]]@(0x529F, 0x80FD, 0x5EFA, 0x8BAE))
$userIds = [System.Collections.Generic.List[long]]::new()
$storageKeys = [System.Collections.Generic.List[string]]::new()

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Invoke-Request {
    param([string]$Method, [string]$Path, [string]$Token, $Body)
    $params = @{ Uri = "$baseUrl$Path"; Method = $Method; TimeoutSec = 20 }
    if ($Token) { $params.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json; charset=utf-8'
        $json = $Body | ConvertTo-Json -Depth 10
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    try {
        return Invoke-RestMethod @params
    } catch {
        if ($_.ErrorDetails.Message) {
            return $_.ErrorDetails.Message | ConvertFrom-Json
        }
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Dispose()
            if ($content) { return $content | ConvertFrom-Json }
        }
        throw
    }
}

function Invoke-Success {
    param([string]$Method, [string]$Path, [string]$Token, $Body)
    $response = Invoke-Request -Method $Method -Path $Path -Token $Token -Body $Body
    Assert-True ($response.code -eq 200) "$Method $Path failed: $($response.code) $($response.message)"
    return $response
}

function Invoke-Failure {
    param([string]$Method, [string]$Path, [string]$Token, $Body)
    $response = Invoke-Request -Method $Method -Path $Path -Token $Token -Body $Body
    Assert-True ($response.code -ne 200) "$Method $Path unexpectedly succeeded"
    return $response
}

function Login([string]$username, [string]$loginPassword) {
    return (Invoke-Success -Method Post -Path '/api/auth/login' -Body @{
        username = $username; password = $loginPassword
    }).data.accessToken
}

try {
    $adminToken = Login 'admin' '123456'
    $register1 = Invoke-Success -Method Post -Path '/api/auth/register' -Body @{
        username = $username1; password = $password; nickname = $username1
    }
    $register2 = Invoke-Success -Method Post -Path '/api/auth/register' -Body @{
        username = $username2; password = $password; nickname = $username2
    }
    $token1 = $register1.data.accessToken
    $token2 = $register2.data.accessToken
    $user1 = (Invoke-Success -Method Get -Path '/api/auth/userinfo' -Token $token1).data
    $user2 = (Invoke-Success -Method Get -Path '/api/auth/userinfo' -Token $token2).data
    $userId1 = [long]$user1.user_id
    $userId2 = [long]$user2.user_id
    $userIds.Add($userId1)
    $userIds.Add($userId2)

    $course = Invoke-Success -Method Post -Path '/api/admin/courses' -Token $adminToken -Body @{
        course_name = $courseName; course_code = $courseCode; course_type = 2; credit = 2
        teacher_id = 2; category_id = 1; status = 1; description = 'third round smoke'
    }
    $courseId = [long]$course.data.course_id

    Invoke-Failure -Method Post -Path '/api/favorite/add' -Token $token1 -Body @{
        type = 'post'; target_id = $courseId
    } | Out-Null
    Invoke-Failure -Method Post -Path '/api/favorite/add' -Token $token1 -Body @{
        type = 'course'; target_id = 999999999
    } | Out-Null
    Invoke-Success -Method Post -Path '/api/favorite/add' -Token $token1 -Body @{
        type = 'course'; target_id = $courseId
    } | Out-Null
    Invoke-Success -Method Post -Path '/api/favorite/add' -Token $token1 -Body @{
        type = 'course'; target_id = $courseId
    } | Out-Null
    $favorites = Invoke-Success -Method Get -Path '/api/favorite/list?type=course' -Token $token1
    Assert-True (@($favorites.data | Where-Object targetId -eq $courseId).Count -eq 1) 'Duplicate favorite row detected'
    Invoke-Failure -Method Post -Path '/api/favorite/remove' -Token $token2 -Body @{
        type = 'course'; target_id = $courseId
    } | Out-Null

    Invoke-Failure -Method Post -Path "/api/course/$courseId/review" -Token $token1 -Body @{
        rating = 5; content = 'not selected'
    } | Out-Null
    Invoke-Success -Method Post -Path "/api/course/$courseId/select" -Token $token1 | Out-Null
    Invoke-Failure -Method Post -Path "/api/course/$courseId/review" -Token $token1 -Body @{
        rating = 6; content = 'invalid rating'
    } | Out-Null
    Invoke-Success -Method Post -Path "/api/course/$courseId/review" -Token $token1 -Body @{
        rating = 5; content = 'valid review'; isAnonymous = 0
    } | Out-Null
    Invoke-Failure -Method Post -Path "/api/course/$courseId/review" -Token $token1 -Body @{
        rating = 4; content = 'duplicate review'
    } | Out-Null
    Invoke-Failure -Method Delete -Path "/api/course/$courseId/review" -Token $token2 | Out-Null
    Invoke-Success -Method Delete -Path "/api/course/$courseId/review" -Token $token1 | Out-Null

    $post = Invoke-Success -Method Post -Path '/api/square' -Token $token1 -Body @{
        content = $postContent; tags = 'third-round'
    }
    $postId = [long]$post.data.post_id
    Invoke-Failure -Method Post -Path '/api/square/999999999/comments' -Token $token1 -Body @{ content = 'missing' } | Out-Null
    Invoke-Failure -Method Post -Path "/api/square/$postId/comments" -Token $token2 -Body @{ content = '   ' } | Out-Null
    Invoke-Success -Method Post -Path "/api/square/$postId/comments" -Token $token2 -Body @{ content = 'user two comment' } | Out-Null
    $comments = Invoke-Success -Method Get -Path "/api/square/$postId/comments" -Token $token1
    $commentId = [long](@($comments.data) | Select-Object -First 1).comment_id
    Invoke-Failure -Method Delete -Path "/api/square/comments/$commentId" -Token $token1 | Out-Null
    Invoke-Success -Method Delete -Path "/api/admin/square/comments/$commentId" -Token $adminToken | Out-Null
    Invoke-Failure -Method Post -Path '/api/square/999999999/like' -Token $token1 | Out-Null
    $liked = Invoke-Success -Method Post -Path "/api/square/$postId/like" -Token $token1
    $unliked = Invoke-Success -Method Post -Path "/api/square/$postId/like" -Token $token1
    Assert-True ($liked.data.liked -and -not $unliked.data.liked) 'Like toggle state mismatch'

    $uploadRaw = & curl.exe -sS -X POST "$baseUrl/api/material/upload" `
        -H "Authorization: Bearer $token1" `
        -F "file=@$fixture;type=text/plain" `
        -F "materialName=$materialName" -F 'materialType=1' -F "courseId=$courseId" `
        -F 'downloadScore=0' -F 'keyword=third-round'
    $upload = $uploadRaw | ConvertFrom-Json
    Assert-True ($upload.code -eq 200) "Material upload failed: $($upload.message)"
    $uploads = Invoke-Success -Method Get -Path '/api/material/my' -Token $token1
    $materialId = [long]((@($uploads.data) | Where-Object material_name -eq $materialName | Select-Object -First 1).material_id)
    Invoke-Failure -Method Post -Path '/api/favorite/add' -Token $token1 -Body @{
        type = 'material'; target_id = $materialId
    } | Out-Null
    Invoke-Success -Method Put -Path "/api/admin/materials/$materialId/approve" -Token $adminToken | Out-Null
    Invoke-Success -Method Post -Path '/api/favorite/add' -Token $token1 -Body @{
        type = 'material'; target_id = $materialId
    } | Out-Null
    Invoke-Failure -Method Post -Path "/api/material/$materialId/report" -Token $token1 -Body @{ reason = '   ' } | Out-Null
    Invoke-Success -Method Post -Path "/api/material/$materialId/report" -Token $token1 -Body @{ reason = 'invalid material content' } | Out-Null
    Invoke-Failure -Method Post -Path "/api/material/$materialId/report" -Token $token1 -Body @{ reason = 'duplicate report' } | Out-Null
    Invoke-Success -Method Put -Path "/api/admin/materials/$materialId/deactivate" -Token $adminToken | Out-Null
    $materialFavorites = Invoke-Success -Method Get -Path '/api/favorite/list?type=material' -Token $token1
    Assert-True (@($materialFavorites.data | Where-Object targetId -eq $materialId).Count -eq 0) 'Inactive material favorite was not filtered'
    $reports = Invoke-Success -Method Get -Path '/api/admin/reports?page=1&pageSize=100' -Token $adminToken
    Assert-True (@($reports.data.list | Where-Object material_id -eq $materialId).Count -eq 0) 'Invalid report target was not filtered'

    Invoke-Failure -Method Post -Path '/api/feedback' -Token $token1 -Body @{
        type = 'feature'; content = 'invalid type'
    } | Out-Null
    Invoke-Failure -Method Post -Path '/api/feedback' -Token $token1 -Body @{
        type = $feedbackType; content = '   '
    } | Out-Null
    Invoke-Success -Method Post -Path '/api/feedback' -Token $token1 -Body @{
        type = $feedbackType; content = 'third round feedback'; contact = 'codex@example.com'
    } | Out-Null

    Write-Host 'Third-round API smoke passed'
}
finally {
    $keyOutput = & $mysql --host=127.0.0.1 --port=3306 --user=bgss_user `
        --batch --skip-column-names --execute="SELECT storage_key FROM bgss_xtt.material WHERE material_name='$materialName';"
    foreach ($key in @($keyOutput)) {
        if ($key) { $storageKeys.Add([string]$key) }
    }
    $ids = if ($userIds.Count -gt 0) { $userIds -join ',' } else { '0' }
    $cleanupSql = @"
USE bgss_xtt;
DELETE FROM square_like WHERE user_id IN ($ids);
DELETE c FROM square_comment c JOIN square_post p ON c.post_id=p.post_id WHERE p.user_id IN ($ids);
DELETE FROM square_post WHERE user_id IN ($ids);
DELETE FROM material_report WHERE reporter_id IN ($ids);
DELETE FROM material_download WHERE user_id IN ($ids);
DELETE FROM material WHERE uploader_id IN ($ids);
DELETE FROM favorite WHERE user_id IN ($ids);
DELETE FROM course_selection WHERE user_id IN ($ids);
DELETE FROM course_review WHERE user_id IN ($ids);
DELETE FROM score_record WHERE user_id IN ($ids);
DELETE FROM feedback WHERE user_id IN ($ids);
DELETE FROM user WHERE user_id IN ($ids);
DELETE FROM course WHERE course_code='$courseCode';
"@
    & $mysql --host=127.0.0.1 --port=3306 --user=bgss_user --execute=$cleanupSql
    foreach ($key in $storageKeys) {
        $path = Join-Path (Join-Path $root 'uploads') $key
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
}
