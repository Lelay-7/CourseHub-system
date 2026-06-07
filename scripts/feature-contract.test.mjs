import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const read = path => readFileSync(resolve(root, path), 'utf8')

const checks = [
  ['src/api/course.js', 'submitCourseReview'],
  ['src/api/material.js', 'uploadMaterial'],
  ['src/api/material.js', 'reportMaterial'],
  ['src/api/auth.js', 'uploadAvatar'],
  ['src/api/auth.js', 'register'],
  ['src/api/mock.js', '/api/course/'],
  ['src/api/mock.js', '/review'],
  ['src/api/mock.js', '/api/material/upload'],
  ['src/api/mock.js', '/report'],
  ['src/api/mock.js', '/api/user/avatar'],
  ['src/api/mock.js', '/api/auth/register'],
  ['src/stores/user.js', 'register'],
  ['src/views/login/LoginView.vue', 'handleRegister'],
  ['src/views/course/CourseDetailView.vue', 'showReviewDialog'],
  ['src/views/course/CourseDetailView.vue', 'submitReview'],
  ['src/views/course/CourseListView.vue', 'teacherOptions'],
  ['src/views/material/MaterialListView.vue', 'showUploadDialog'],
  ['src/views/material/MaterialListView.vue', 'submitUpload'],
  ['src/views/material/MaterialListView.vue', 'courseId'],
  ['src/views/material/MaterialDetailView.vue', 'reportMaterial'],
  ['src/views/profile/ProfileView.vue', 'authApi.uploadAvatar']
]

const failures = checks.filter(([file, text]) => !read(file).includes(text))

if (failures.length > 0) {
  console.error('Missing feature contracts:')
  for (const [file, text] of failures) {
    console.error(`- ${file} should include ${text}`)
  }
  process.exit(1)
}

console.log('Feature contracts passed')
