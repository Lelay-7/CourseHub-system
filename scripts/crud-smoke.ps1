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
$prefix = "cx$stamp"
$username1 = "${prefix}a"
$username2 = "${prefix}b"
$password = 'Smoke123'
$courseCode = "C$stamp"
$courseName = "Codex CRUD $stamp"
$bannerTitle = "Codex Banner $stamp"
$goodsName = "Codex Goods $stamp"
$materialName = "Codex Material $stamp"
$postContent = "Codex post $stamp"
$feedbackContent = "Codex feedback $stamp"
$results = [System.Collections.Generic.List[object]]::new()
$userIds = [System.Collections.Generic.List[long]]::new()
$storageKeys = [System.Collections.Generic.List[string]]::new()

function Add-Result([string]$scope, [string]$operation, [string]$status) {
    $results.Add([pscustomobject]@{ Scope = $scope; Operation = $operation; Status = $status })
    Write-Host "[$status] $scope - $operation"
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token,
        $Body
    )
    $params = @{
        Uri = "$baseUrl$Path"
        Method = $Method
        TimeoutSec = 20
    }
    if ($Token) { $params.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = $Body | ConvertTo-Json -Depth 10
    }
    try {
        $response = Invoke-RestMethod @params
    } catch {
        $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        throw "$Method $Path HTTP failure: $detail"
    }
    if ($response.code -ne 200) {
        throw "$Method $Path failed: $($response.code) $($response.message)"
    }
    return $response
}

function Login([string]$username, [string]$loginPassword) {
    $response = Invoke-Api -Method Post -Path '/api/auth/login' -Body @{
        username = $username
        password = $loginPassword
    }
    return $response.data.accessToken
}

function Current-User([string]$token) {
    return (Invoke-Api -Method Get -Path '/api/auth/userinfo' -Token $token).data
}

try {
    $adminToken = Login 'admin' '123456'
    Add-Result 'admin' 'login' 'PASS'

    $register1 = Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
        username = $username1; password = $password; nickname = $username1
    }
    $register2 = Invoke-Api -Method Post -Path '/api/auth/register' -Body @{
        username = $username2; password = $password; nickname = $username2
    }
    $userToken1 = $register1.data.accessToken
    $userToken2 = $register2.data.accessToken
    $user1 = Current-User $userToken1
    $user2 = Current-User $userToken2
    $userId1 = [long]$user1.user_id
    $userId2 = [long]$user2.user_id
    $userIds.Add($userId1)
    $userIds.Add($userId2)
    & $mysql --host=127.0.0.1 --port=3306 --user=bgss_user `
        --execute="UPDATE bgss_xtt.user SET score_balance=100,total_score=100 WHERE user_id=$userId1;"
    Add-Result 'user' 'register temporary users' 'PASS'

    $course = Invoke-Api -Method Post -Path '/api/admin/courses' -Token $adminToken -Body @{
        course_name = $courseName; course_code = $courseCode; course_type = 2; credit = 2
        teacher_id = 2; semester = '2026-2027-1'; schedule = 'Friday'; location = 'Codex room'
        category_id = 1; description = 'CRUD smoke test'
    }
    $courseId = [long]$course.data.course_id
    Assert-True ($courseId -gt 0) 'Course id missing after create'
    Add-Result 'admin' 'create course' 'PASS'

    try {
        Invoke-RestMethod -Uri "$baseUrl/api/admin/courses" -Method Post `
            -Headers @{ Authorization = "Bearer $userToken1" } -ContentType 'application/json' `
            -Body (@{ course_name='Forbidden'; course_code="F$stamp"; course_type=2; credit=2 } | ConvertTo-Json) | Out-Null
        throw 'Normal user unexpectedly accessed admin course creation'
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 403) {
            Add-Result 'security' 'normal user blocked from admin create' 'PASS'
        } elseif ($_.Exception.Message -eq 'Normal user unexpectedly accessed admin course creation') {
            throw
        } else {
            throw "Unexpected admin authorization result: $($_.Exception.Message)"
        }
    }

    $duplicate = Invoke-RestMethod -Uri "$baseUrl/api/admin/courses" -Method Post `
        -Headers @{ Authorization = "Bearer $adminToken" } -ContentType 'application/json' `
        -Body (@{ course_name='Duplicate'; course_code=$courseCode; course_type=2; credit=2 } | ConvertTo-Json)
    Assert-True ($duplicate.code -eq 400) 'Duplicate course code was not rejected with a business error'
    Add-Result 'admin' 'reject duplicate course code' 'PASS'

    $banner = Invoke-Api -Method Post -Path '/api/admin/banners' -Token $adminToken -Body @{
        title = $bannerTitle; image_url = 'https://example.com/codex.png'; link_url = '/home'; sort_order = 99; status = 1
    }
    $bannerId = [long]$banner.data.banner_id
    Invoke-Api -Method Delete -Path "/api/admin/banners/$bannerId" -Token $adminToken | Out-Null
    Add-Result 'admin' 'create/delete banner' 'PASS'

    $goods = Invoke-Api -Method Post -Path '/api/admin/goods' -Token $adminToken -Body @{
        goods_name = $goodsName; category = 2; price_score = 1; stock = 5; exchange_limit = 2
        status = 1; description = 'CRUD smoke test'
    }
    $goodsId = [long]$goods.data.goods_id
    Add-Result 'admin' 'create goods' 'PASS'

    $review1 = Invoke-Api -Method Post -Path "/api/course/$courseId/review" -Token $userToken1 -Body @{
        rating = 5; content = 'User delete smoke test'; isAnonymous = 0
    }
    Invoke-Api -Method Delete -Path "/api/course/$courseId/review" -Token $userToken1 | Out-Null
    Add-Result 'user' 'create/delete own course review' 'PASS'

    $review2 = Invoke-Api -Method Post -Path "/api/course/$courseId/review" -Token $userToken1 -Body @{
        rating = 4; content = 'Admin delete smoke test'; isAnonymous = 0
    }
    $reviewId = [long]$review2.data.reviewId
    Invoke-Api -Method Delete -Path "/api/admin/reviews/$reviewId" -Token $adminToken | Out-Null
    Add-Result 'admin' 'delete course review' 'PASS'

    Invoke-Api -Method Post -Path '/api/favorite/add' -Token $userToken1 -Body @{
        type = 'course'; target_id = $courseId
    } | Out-Null
    Invoke-Api -Method Post -Path '/api/favorite/remove' -Token $userToken1 -Body @{
        type = 'course'; target_id = $courseId
    } | Out-Null
    Add-Result 'user' 'add/remove favorite' 'PASS'

    Invoke-Api -Method Post -Path "/api/contact/add/$userId2" -Token $userToken1 | Out-Null
    $pending = Invoke-Api -Method Get -Path '/api/contact/pending' -Token $userToken2
    $friendRequest = @($pending.data) | Where-Object { [long]$_.user_id -eq $userId1 } | Select-Object -First 1
    Assert-True ($null -ne $friendRequest) 'Friend request not visible to target user'
    $friendId = [long]$friendRequest.friend_id
    Invoke-Api -Method Post -Path "/api/contact/accept/$friendId" -Token $userToken2 | Out-Null
    Invoke-Api -Method Delete -Path "/api/contact/remove/$friendId" -Token $userToken1 | Out-Null
    Add-Result 'user' 'add/accept/delete friend' 'PASS'

    $post = Invoke-Api -Method Post -Path '/api/square' -Token $userToken1 -Body @{
        content = $postContent; course_name = $courseName; tags = 'codex,smoke'
    }
    $postId = [long]$post.data.post_id
    if ($postId -le 0) {
        $posts = Invoke-Api -Method Get -Path '/api/square' -Token $userToken1
        $postId = [long]((@($posts.data.list) | Where-Object content -eq $postContent | Select-Object -First 1).post_id)
    }
    $foreignPostDelete = Invoke-RestMethod -Uri "$baseUrl/api/square/$postId" -Method Delete `
        -Headers @{ Authorization = "Bearer $userToken2" }
    Assert-True ($foreignPostDelete.code -eq 400) 'Another user was able to delete the post'
    Add-Result 'security' 'user cannot delete another user post' 'PASS'
    Invoke-Api -Method Post -Path "/api/square/$postId/comments" -Token $userToken2 -Body @{ content = 'Codex comment' } | Out-Null
    $liked = Invoke-Api -Method Post -Path "/api/square/$postId/like" -Token $userToken1
    $unliked = Invoke-Api -Method Post -Path "/api/square/$postId/like" -Token $userToken1
    Assert-True ($liked.data.liked -eq $true -and $unliked.data.liked -eq $false) 'Like toggle did not add then remove'
    Invoke-Api -Method Delete -Path "/api/square/$postId" -Token $userToken1 | Out-Null
    Add-Result 'user' 'create/comment/like/unlike/delete post' 'PASS'

    $adminPost = Invoke-Api -Method Post -Path '/api/square' -Token $userToken1 -Body @{
        content = "$postContent admin-delete"; tags = 'codex'
    }
    $adminPostId = [long]$adminPost.data.post_id
    if ($adminPostId -le 0) {
        $posts = Invoke-Api -Method Get -Path '/api/square' -Token $userToken1
        $adminPostId = [long]((@($posts.data.list) | Where-Object content -eq "$postContent admin-delete" | Select-Object -First 1).post_id)
    }
    Invoke-Api -Method Delete -Path "/api/admin/square/$adminPostId" -Token $adminToken | Out-Null
    Add-Result 'admin' 'delete square post' 'PASS'

    Invoke-Api -Method Post -Path '/api/feedback' -Token $userToken1 -Body @{
        type = 'feature'; content = $feedbackContent; contact = 'codex@example.com'
    } | Out-Null
    $feedbacks = Invoke-Api -Method Get -Path '/api/feedback/my' -Token $userToken1
    Assert-True (@($feedbacks.data | Where-Object content -eq $feedbackContent).Count -eq 1) 'Submitted feedback not found'
    Add-Result 'user' 'create feedback' 'PASS'

    $uploadRaw = & curl.exe -sS -X POST "$baseUrl/api/material/upload" `
        -H "Authorization: Bearer $userToken1" `
        -F "file=@$fixture;type=text/plain" `
        -F "materialName=$materialName" -F 'materialType=1' -F "courseId=$courseId" `
        -F 'downloadScore=0' -F 'keyword=codex'
    $upload = $uploadRaw | ConvertFrom-Json
    Assert-True ($upload.code -eq 200) "Material upload failed: $($upload.message)"
    Add-Result 'user' 'upload material' 'PASS (no user delete endpoint)'
    $uploads = Invoke-Api -Method Get -Path '/api/material/my' -Token $userToken1
    $materialId = [long]((@($uploads.data) | Where-Object material_name -eq $materialName | Select-Object -First 1).material_id)
    Assert-True ($materialId -gt 0) 'Uploaded material not found in user uploads'
    Invoke-Api -Method Put -Path "/api/admin/materials/$materialId/deactivate" -Token $adminToken | Out-Null
    Add-Result 'admin' 'deactivate uploaded material' 'PASS'

    $exchange = Invoke-Api -Method Post -Path '/api/mall/exchange' -Token $userToken1 -Body @{ goodsId = $goodsId }
    $orderId = [long]$exchange.data.orderId
    $foreignOrderDelete = Invoke-RestMethod -Uri "$baseUrl/api/order/$orderId" -Method Delete `
        -Headers @{ Authorization = "Bearer $userToken2" }
    Assert-True ($foreignOrderDelete.code -eq 400) 'Another user was able to delete the order'
    Add-Result 'security' 'user cannot delete another user order' 'PASS'
    Invoke-Api -Method Post -Path "/api/order/$orderId/cancel" -Token $userToken1 | Out-Null
    Invoke-Api -Method Delete -Path "/api/order/$orderId" -Token $userToken1 | Out-Null
    Add-Result 'user' 'exchange/cancel/delete order' 'PASS'

    Invoke-Api -Method Delete -Path "/api/admin/goods/$goodsId" -Token $adminToken | Out-Null
    Add-Result 'admin' 'delete goods' 'PASS'

    Invoke-Api -Method Delete -Path "/api/admin/courses/$courseId" -Token $adminToken | Out-Null
    $courseList = Invoke-Api -Method Get -Path "/api/admin/courses?keyword=$courseCode&page=1&pageSize=10" -Token $adminToken
    Assert-True (@($courseList.data.list).Count -eq 0) 'Deleted course is still listed'
    Add-Result 'admin' 'delete course' 'PASS'
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
DELETE FROM bgss_xtt.square_like WHERE user_id IN ($ids);
DELETE c FROM bgss_xtt.square_comment c JOIN bgss_xtt.square_post p ON c.post_id=p.post_id WHERE p.user_id IN ($ids);
DELETE FROM bgss_xtt.square_post WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.material_report WHERE reporter_id IN ($ids);
DELETE FROM bgss_xtt.material_download WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.material WHERE uploader_id IN ($ids);
DELETE FROM bgss_xtt.favorite WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.friend WHERE user_id IN ($ids) OR target_id IN ($ids);
DELETE FROM bgss_xtt.feedback WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.course_review WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.score_record WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.``order`` WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.user WHERE user_id IN ($ids);
DELETE FROM bgss_xtt.course WHERE course_code='$courseCode';
DELETE FROM bgss_xtt.banner WHERE title='$bannerTitle';
DELETE FROM bgss_xtt.goods WHERE goods_name='$goodsName';
"@
    & $mysql --host=127.0.0.1 --port=3306 --user=bgss_user --execute=$cleanupSql

    $uploadRoot = [System.IO.Path]::GetFullPath((Join-Path $root 'uploads'))
    foreach ($key in $storageKeys) {
        $candidate = [System.IO.Path]::GetFullPath((Join-Path $uploadRoot $key))
        if ($candidate.StartsWith($uploadRoot, [System.StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $candidate)) {
            Remove-Item -LiteralPath $candidate -Force
        }
    }
}

$results | Format-Table -AutoSize
