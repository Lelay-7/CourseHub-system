$ErrorActionPreference = 'Stop'
$baseUrl = if ($env:API_BASE_URL) { $env:API_BASE_URL } else { 'http://localhost:8080' }

Write-Host '========================================'
Write-Host '  API Smoke Test'
Write-Host "  Target: $baseUrl"
Write-Host '========================================'

$results = [System.Collections.Generic.List[object]]::new()
$pass = 0
$fail = 0
$q = 'page=1&pageSize=5'

function Invoke-Check {
  param([string]$Label, [string]$Method, [string]$Path, [string]$Token, $Body,
        [int]$ExpectCode = 200, [switch]$ExpectFile, [string]$Query = '')
  try {
    $uri = "$baseUrl$Path"
    if ($Query) { $uri = "$uri" + '?' + $Query }
    $params = @{ Uri = $uri; Method = $Method; TimeoutSec = 15 }
    if ($Token) { $params.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) { $params.ContentType = 'application/json'; $params.Body = ($Body | ConvertTo-Json -Depth 4) }
    $response = Invoke-RestMethod @params
    $code = $response.code
    if ($ExpectFile) {
      $results.Add([pscustomobject]@{ Test = $Label; Status = 'PASS'; Detail = '' })
      $script:pass++
    } elseif ($code -eq $ExpectCode) {
      $results.Add([pscustomobject]@{ Test = $Label; Status = 'PASS'; Detail = '' })
      $script:pass++
    } else {
      $results.Add([pscustomobject]@{ Test = $Label; Status = 'FAIL'; Detail = "expected $ExpectCode, got $code" })
      $script:fail++
    }
  } catch {
    $ex = $_.Exception
    if ($ExpectCode -ne 200 -and $ex -is [System.Net.WebException]) {
      $httpCode = [int]$ex.Response.StatusCode
      if ($httpCode -eq $ExpectCode) {
        $results.Add([pscustomobject]@{ Test = $Label; Status = 'PASS'; Detail = "HTTP $httpCode as expected" })
        $script:pass++
        return
      }
    }
    $detail = if ($_.ErrorDetails.Message) { $_.ErrorDetails.Message } else { $ex.Message }
    $results.Add([pscustomobject]@{ Test = $Label; Status = 'FAIL'; Detail = ($detail.Substring(0, [Math]::Min(80, $detail.Length))) })
    $script:fail++
  }
}

function Invoke-FailureExpected {
  param([string]$Label, [string]$Method, [string]$Path, [string]$Token, $Body, [string]$Query = '')
  try {
    $uri = "$baseUrl$Path"
    if ($Query) { $uri = "$uri" + '?' + $Query }
    $params = @{ Uri = $uri; Method = $Method; TimeoutSec = 10 }
    if ($Token) { $params.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) { $params.ContentType = 'application/json'; $params.Body = ($Body | ConvertTo-Json -Depth 4) }
    $response = Invoke-RestMethod @params
    if ($response.code -ne 200) {
      $results.Add([pscustomobject]@{ Test = $Label; Status = 'PASS'; Detail = 'correctly rejected' })
      $script:pass++
    } else {
      $results.Add([pscustomobject]@{ Test = $Label; Status = 'FAIL'; Detail = 'expected rejection, got 200' })
      $script:fail++
    }
  } catch {
    $results.Add([pscustomobject]@{ Test = $Label; Status = 'PASS'; Detail = 'correctly rejected' })
    $script:pass++
  }
}

# ==== Login ====
Write-Host '[Login]'
Invoke-Check -Label 'student login' -Method Post -Path '/api/auth/login' -Body @{ username = '2024051224'; password = '123456' }
$sr = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"2024051224","password":"123456"}' -TimeoutSec 10
$studentToken = $sr.data.accessToken

Invoke-Check -Label 'admin login' -Method Post -Path '/api/auth/login' -Body @{ username = 'admin'; password = '123456' }
$ar = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}' -TimeoutSec 10
$adminToken = $ar.data.accessToken

Invoke-Check -Label 'teacher login' -Method Post -Path '/api/auth/login' -Body @{ username = 'chenming'; password = '123456' }

# ==== Student APIs ====
Write-Host '[Student]'
Invoke-Check -Label 'GET /api/auth/userinfo' -Method Get -Path '/api/auth/userinfo' -Token $studentToken
Invoke-Check -Label 'GET /api/course/list' -Method Get -Path '/api/course/list' -Token $studentToken -Query $q
Invoke-Check -Label 'GET /api/course/selected' -Method Get -Path '/api/course/selected' -Token $studentToken
Invoke-Check -Label 'GET /api/material/list' -Method Get -Path '/api/material/list' -Token $studentToken -Query $q
Invoke-Check -Label 'GET /api/mall/goods' -Method Get -Path '/api/mall/goods' -Token $studentToken -Query $q
Invoke-Check -Label 'GET /api/square' -Method Get -Path '/api/square' -Token $studentToken -Query $q

# ==== Downloads ====
Write-Host '[Downloads]'
Invoke-Check -Label 'download material 1 (PDF)' -Method Get -Path '/api/material/1/download' -Token $studentToken -ExpectFile
Invoke-Check -Label 'download material 2 (PPTX)' -Method Get -Path '/api/material/2/download' -Token $studentToken -ExpectFile
Invoke-Check -Label 'download material 6 (XLSX)' -Method Get -Path '/api/material/6/download' -Token $studentToken -ExpectFile

# ==== Permissions ====
Write-Host '[Permissions]'
Invoke-Check -Label 'student -> admin dashboard' -Method Get -Path '/api/admin/dashboard' -Token $studentToken -ExpectCode 403
Invoke-Check -Label 'student -> admin users' -Method Get -Path '/api/admin/users' -Token $studentToken -Query $q -ExpectCode 403

# ==== Mall ====
Write-Host '[Mall]'
Invoke-FailureExpected -Label 'out-of-stock exchange' -Method Post -Path '/api/mall/exchange' -Token $studentToken -Body @{ goodsId = 5 }

# ==== Admin ====
Write-Host '[Admin]'
Invoke-Check -Label 'GET /api/admin/dashboard' -Method Get -Path '/api/admin/dashboard' -Token $adminToken
Invoke-Check -Label 'GET /api/admin/users' -Method Get -Path '/api/admin/users' -Token $adminToken -Query $q
Invoke-Check -Label 'GET /api/admin/materials' -Method Get -Path '/api/admin/materials' -Token $adminToken -Query $q

# ==== Result ====
Write-Host ''
Write-Host '========================================'
$results | Format-Table -AutoSize
Write-Host "TOTAL: $($results.Count)  |  PASS: $pass  |  FAIL: $fail"
if ($fail -gt 0) { Write-Host 'SMOKE: FAILED'; exit 1 } else { Write-Host 'SMOKE: PASSED'; exit 0 }
