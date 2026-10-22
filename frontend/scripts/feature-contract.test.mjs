import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const read = path => readFileSync(resolve(root, path), 'utf8')

const checks = [
  ['src/api/course.js', 'submitCourseReview'],
  ['src/api/course.js', 'getSelectedCourses'],
  ['src/api/course.js', 'selectCourse'],
  ['src/api/course.js', 'dropCourse'],
  ['src/api/course.js', 'getMyReviews'],
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
  ['src/views/course/CourseListView.vue', 'selection_count'],
  ['src/views/course/CourseListView.vue', 'avg_rating'],
  ['src/views/course/CourseListView.vue', 'timeSlot: filters.timeSlot || undefined'],
  ['src/views/course/CourseListView.vue', 'currentPage.value = 1'],
  ['src/views/course/CourseListView.vue', 'el-pagination'],
  ['src/views/profile/FavoriteView.vue', 'Array.isArray(data)'],
  ['src/views/profile/FavoriteView.vue', 'if (type === favType.value) await fetchList()'],
  ['src/views/profile/FavoriteView.vue', '@tab-change="resetAndFetch"'],
  ['src/views/profile/FavoriteView.vue', '<ListPagination'],
  ['src/views/profile/FavoriteView.vue', "item.courseName || '内容已失效'"],
  ['src/api/square.js', 'deleteComment'],
  ['src/views/square/SquareView.vue', 'handleDeleteComment'],
  ['src/views/square/SquareView.vue', 'maxlength="500"'],
  ['src/api/admin.js', 'deleteSquareComment'],
  ['src/api/admin.js', 'listSquareComments'],
  ['src/views/admin/SquareManageView.vue', '评论管理'],
  ['src/views/profile/FeedbackView.vue', 'maxlength="1000"'],
  ['src/views/profile/MyCoursesView.vue', 'getSelectedCourses'],
  ['src/views/profile/MyReviewsView.vue', 'getMyReviews'],
  ['src/views/material/MaterialListView.vue', '已下架'],
  ['src/views/material/MaterialListView.vue', 'showUploadDialog'],
  ['src/views/material/MaterialListView.vue', '<MaterialEditor'],
  ['src/components/MaterialEditor.vue', '@click="submit"'],
  ['src/components/MaterialEditor.vue', 'await api.uploadMaterial(data)'],
  ['src/components/MaterialEditor.vue', 'await api.resubmitMaterial(editing.material_id, data)'],
  ['src/views/material/MaterialListView.vue', 'courseId'],
  ['src/views/material/MaterialListView.vue', 'priceFilter: priceFilter.value'],
  ['src/views/material/MaterialListView.vue', 'currentPage.value = 1'],
  ['src/views/material/MaterialListView.vue', 'el-pagination'],
  ['src/views/material/MaterialDetailView.vue', 'reportMaterial'],
  ['src/views/profile/ProfileView.vue', 'authApi.uploadAvatar'],
  ['src/views/admin/UserManageView.vue', 'filterStatus'],
  ['src/views/admin/UserManageView.vue', 'el-pagination'],
  ['src/views/admin/OrderManageView.vue', 'completeOrder'],
  ['src/views/admin/OrderManageView.vue', '@click="processOrder">确认完成'],
  ['src/api/admin.js', '/complete'],
  ['src/api/admin.js', 'listGoods'],
  ['src/views/admin/GoodsManageView.vue', 'listGoods'],
  ['src/views/admin/GoodsManageView.vue', 'exchange_limit'],
  ['src/views/admin/CourseManageView.vue', '课程状态']
]

const forbidden = [
  ['src/views/admin/OrderManageView.vue', '发货'],
  ['src/views/admin/GoodsManageView.vue', "request.get('/api/mall/goods'"],
  ['src/views/course/CourseListView.vue', 'map[filters.timeSlot].some'],
  ['src/views/material/MaterialListView.vue', 'materialList.value.filter(item => !item.download_score)'],
  ['src/views/material/MaterialListView.vue', 'materialList.value.filter(item => item.download_score > 0)']
]

const failures = checks.filter(([file, text]) => !read(file).includes(text))

if (failures.length > 0) {
  console.error('Missing feature contracts:')
  for (const [file, text] of failures) {
    console.error(`- ${file} should include ${text}`)
  }
  process.exit(1)
}

const forbiddenFailures = forbidden.filter(([file, text]) => read(file).includes(text))
if (forbiddenFailures.length > 0) {
  console.error('Forbidden legacy contracts:')
  for (const [file, text] of forbiddenFailures) {
    console.error(`- ${file} must not include ${text}`)
  }
  process.exit(1)
}

console.log('Feature contracts passed')
