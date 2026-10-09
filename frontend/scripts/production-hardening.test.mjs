import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..', '..')
const read = path => readFileSync(resolve(root, path), 'utf8')

const materialApi = read('frontend/src/api/material.js')
const authApi = read('frontend/src/api/auth.js')
const requestApi = read('frontend/src/api/request.js')
const session = read('frontend/src/utils/session.js')
const materialList = read('frontend/src/views/material/MaterialListView.vue')
const materialDetail = read('frontend/src/views/material/MaterialDetailView.vue')
const profile = read('frontend/src/views/profile/ProfileView.vue')
const editProfile = read('frontend/src/views/profile/EditProfileView.vue')
const home = read('frontend/src/views/home/HomeView.vue')
const contactApi = read('frontend/src/api/contact.js')
const selection = read('frontend/src/stores/selection.js')
const user = read('frontend/src/stores/user.js')
const start = read('start.bat')
const applicationConfig = read('backend/src/main/resources/application.yml')

assert.match(materialApi, /new FormData\(\)/, 'material upload must use FormData')
assert.match(materialApi, /responseType:\s*['"]blob['"]/, 'material download must request a blob')
assert.match(authApi, /new FormData\(\)/, 'avatar upload must use FormData')
assert.doesNotMatch(materialList, /readAsDataURL|file_url:\s*fileData/, 'material view must not persist Base64')
assert.doesNotMatch(profile + editProfile, /readAsDataURL/, 'avatar views must not persist Base64')
assert.doesNotMatch(home + contactApi, /fetch\s*\(\s*['"]\/api\//, 'API calls must use configured Axios')
assert.doesNotMatch(materialDetail, /material\.value\.file_url|material\.file_url/, 'material details must not expose stored file URLs')
assert.match(session, /refreshPromise/, 'token refresh must be coordinated by one shared promise')
assert.match(selection, /setCurrentUser|userKey/, 'selection persistence must be user scoped')
assert.match(user, /selectionStore\.(setCurrentUser|clearAll)/, 'authentication changes must switch or clear selection state')
assert.match(start, /if not defined JAVA_HOME/, 'start script must require JAVA_HOME')
assert.doesNotMatch(start, /%USERPROFILE%\\\.jdks|[A-Z]:\\redis/i,
  'start script must not hardcode machine-specific JDK or Redis paths')
assert.match(start, /\.env\.local/, 'start script must load local environment variables')
assert.match(start, /maven\.test\.skip=true/, 'start script must skip test compilation for runtime builds')
assert.match(start, /if errorlevel 1/, 'start script must abort after a failed build')
assert.ok(existsSync(resolve(root, 'stop.ps1')), 'stop.ps1 must exist')
assert.match(applicationConfig, /max-file-size:\s*\$\{FILE_MAX_SIZE:50MB\}/, 'Spring multipart file limit must match material validation')
assert.match(applicationConfig, /max-request-size:\s*\$\{FILE_MAX_REQUEST_SIZE:55MB\}/, 'Spring multipart request limit must include form overhead')

console.log('Production hardening contracts passed.')
