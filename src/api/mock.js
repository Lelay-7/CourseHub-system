const now = Date.now()

function daysAgo(days) {
  return new Date(now - days * 86400000).toISOString()
}

function makeToken() {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const payload = btoa(JSON.stringify({
    sub: '2024051224',
    exp: Math.floor(Date.now() / 1000) + 7 * 86400
  }))
  return `${header}.${payload}.demo`
}

function svgData(title, colorA, colorB) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="540" viewBox="0 0 960 540" role="img" aria-label="${title}"><defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop stop-color="${colorA}"/><stop offset="1" stop-color="${colorB}"/></linearGradient><pattern id="p" width="72" height="72" patternUnits="userSpaceOnUse"><path d="M0 72 72 0M-18 18 18-18M54 90 90 54" stroke="rgba(255,255,255,.18)" stroke-width="2"/></pattern><radialGradient id="shine" cx="28%" cy="20%" r="72%"><stop stop-color="rgba(255,255,255,.22)"/><stop offset="1" stop-color="rgba(255,255,255,0)"/></radialGradient></defs><rect width="960" height="540" fill="url(#g)"/><rect width="960" height="540" fill="url(#p)"/><rect width="960" height="540" fill="url(#shine)"/><circle cx="770" cy="110" r="92" fill="rgba(255,255,255,.18)"/><circle cx="835" cy="430" r="145" fill="rgba(0,0,0,.09)"/><circle cx="120" cy="420" r="74" fill="rgba(255,255,255,.08)"/></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

const covers = [
  svgData('算法与数据结构', '#e87452', '#f4b24f'),
  svgData('Web 前端工程', '#c98272', '#e87452'),
  svgData('数据库系统', '#b56b48', '#8b6f55'),
  svgData('机器学习导论', '#d89334', '#c98272'),
  svgData('软件工程实践', '#86a96f', '#e0b36a'),
  svgData('网络安全基础', '#9a422d', '#e87452')
]

const user = {
  user_id: 1001,
  username: '2024051224',
  nickname: '张义彬',
  real_name: '张义彬',
  user_type: 1,
  department: '计算机学院',
  class_name: '计科246',
  phone: '13800135678',
  avatar_url: '',
  score_balance: 386,
  total_score: 820
}

const categories = [
  { category_id: 1, category_name: '计算机基础' },
  { category_id: 2, category_name: '软件开发' },
  { category_id: 3, category_name: '数据智能' },
  { category_id: 4, category_name: '通识拓展' }
]

const courses = [
  ['算法与数据结构', 'CS203', '陈明', 1, 4, 1, 468, 4.8, '周一 3-4 节', '信达楼 B302'],
  ['Web 前端工程实践', 'SE318', '刘青', 2, 3, 2, 392, 4.7, '周三 5-6 节', '实验楼 A411'],
  ['数据库系统原理', 'CS305', '王雪', 1, 4, 1, 356, 4.6, '周二 1-2 节', '二教 204'],
  ['机器学习导论', 'AI201', '赵启航', 2, 3, 3, 281, 4.9, '周四 7-8 节', '信达楼 C106'],
  ['软件工程与项目管理', 'SE210', '何嘉', 1, 3, 2, 318, 4.5, '周五 3-4 节', '一教 305'],
  ['网络安全基础', 'CS260', '唐越', 2, 2, 1, 245, 4.4, '周二 9-10 节', '实验楼 B208'],
  ['移动应用开发', 'SE335', '李洛', 2, 3, 2, 214, 4.6, '周一 7-8 节', '实验楼 A508'],
  ['数字媒体技术', 'DM101', '周岚', 2, 2, 4, 188, 4.3, '周三 1-2 节', '艺术楼 203']
].map((item, index) => ({
  course_id: index + 1,
  course_name: item[0],
  course_code: item[1],
  teacher_name: item[2],
  course_type: item[3],
  credit: item[4],
  category_id: item[5],
  student_count: item[6],
  avg_rating: item[7],
  schedule: item[8],
  location: item[9],
  cover_url: covers[index % covers.length],
  is_favorited: index % 3 === 0,
  outline: `<p>本课程围绕真实校园学习场景展开，强调概念理解、课堂练习和项目实践。</p><ul><li>第 1-4 周：基础模型与工具链</li><li>第 5-10 周：核心专题与案例拆解</li><li>第 11-16 周：小组项目、展示与复盘</li></ul>`,
  reviews: [
    { review_id: 1, user_name: '张义彬', rating: 5, content: '节奏清楚，课堂案例很贴近实际项目。', create_time: daysAgo(2) },
    { review_id: 2, user_name: '李欣', rating: 4, content: '作业有挑战，但反馈及时，能学到东西。', create_time: daysAgo(5) }
  ]
}))

const materials = [
  ['期末复习提纲与知识图谱', 1, '陈明', 1680, 0, 1],
  ['Vue3 组件设计实验指导书', 2, '刘青', 1268, 8, 2],
  ['数据库 SQL 练习题集', 3, '王雪', 932, 5, 3],
  ['机器学习课程项目模板', 6, '赵启航', 611, 12, 4],
  ['软件工程答辩 PPT 范例', 4, '何嘉', 758, 6, 5],
  ['校园系统界面参考图包', 5, '周岚', 489, 0, 6]
].map((item, index) => ({
  material_id: index + 1,
  material_name: item[0],
  material_type: item[1],
  uploader_name: item[2],
  download_count: item[3],
  download_score: item[4],
  course_id: item[5],
  create_time: daysAgo(index + 1),
  file_url: item[1] === 5 ? covers[index % covers.length] : 'about:blank',
  is_favorited: index % 2 === 0
}))

const contacts = [
  { user_id: 201, real_name: '陈明', user_type: 2, title: '教授', department: '计算机学院', office: '信达楼 B507', phone: '02885961234', email: 'chenming@cuit.edu.cn', email_visible: true, wechat_id: 'cm-course', bio: '研究方向为算法设计与程序分析。' },
  { user_id: 202, real_name: '刘青', user_type: 2, title: '讲师', department: '软件工程学院', office: '实验楼 A412', phone: '02885962345', email: 'liuqing@cuit.edu.cn', email_visible: true, wechat_id: 'frontend-lq', bio: '关注前端工程化、可用性与交互体验。' },
  { user_id: 203, real_name: '王雪', user_type: 2, title: '副教授', department: '计算机学院', office: '二教 514', phone: '02885963456', email: 'wangxue@cuit.edu.cn', email_visible: true, wechat_id: 'db-wx', bio: '主讲数据库系统与数据治理。' },
  { user_id: 301, real_name: '李欣', user_type: 1, department: '计算机学院', class_name: '计科246', student_id: '2024051208', phone: '13911112222', email: 'lixin@student.cuit.edu.cn', email_visible: true, is_authorized: true, wechat_id: 'lixin246', bio: '喜欢数据可视化和校园产品设计。' },
  { user_id: 302, real_name: '周可', user_type: 1, department: '计算机学院', class_name: '计科246', student_id: '2024051216', phone: '13722223333', email: '', email_visible: false, is_authorized: false, wechat_id: '', bio: '该同学暂未开放个人简介。' }
]

const goods = [
  ['校园咖啡券', 1, 88, 32, '兑换后可在校内合作咖啡店核销一杯指定饮品。'],
  ['图书馆研修间预约券', 3, 120, 14, '可优先预约 2 小时研修间，适合小组讨论。'],
  ['云盘扩容 5GB', 2, 160, 80, '为学习资料空间扩容，有效期一学期。'],
  ['定制帆布袋', 1, 220, 9, '印有不高山选课通视觉标识，限量兑换。'],
  ['老师答疑优先卡', 3, 260, 5, '用于课程答疑预约优先排队。'],
  ['校园文创徽章', 1, 55, 0, '小批量文创周边，当前已兑完。']
].map((item, index) => ({
  goods_id: index + 1,
  name: item[0],
  category_id: item[1],
  exchange_score: item[2],
  stock: item[3],
  description: item[4],
  cover_url: svgData(item[0], ['#e87452', '#c98272', '#86a96f', '#f4b24f'][index % 4], ['#9a422d', '#b56b48', '#8b6f55', '#d89334'][index % 4]),
  images: [svgData(item[0], '#e87452', '#f4b24f'), svgData('兑换说明', '#c98272', '#fff0cf')],
  has_exchanged: index === 1
}))

const orders = [
  { order_id: 1, order_no: 'BG20260605001', status: 2, subject: '校园咖啡券', score_amount: 88, create_time: daysAgo(1) },
  { order_id: 2, order_no: 'BG20260603008', status: 3, subject: '云盘扩容 5GB', score_amount: 160, create_time: daysAgo(4) },
  { order_id: 3, order_no: 'BG20260528012', status: 1, subject: '定制帆布袋', score_amount: 220, create_time: daysAgo(6) }
]

const scoreDetails = [
  { id: 1, source: '每日签到', amount: 10, create_time: daysAgo(0) },
  { id: 2, source: '学习 Web 前端工程实践', amount: 15, create_time: daysAgo(1) },
  { id: 3, source: '下载 Vue3 组件设计实验指导书', amount: -8, create_time: daysAgo(2) },
  { id: 4, source: '评价数据库系统原理', amount: 3, create_time: daysAgo(3) },
  { id: 5, source: '兑换校园咖啡券', amount: -88, create_time: daysAgo(5) }
]

function ok(data) {
  return { code: 200, message: 'ok', data }
}

function paginate(list, params = {}) {
  const page = Number(params.page || 1)
  const pageSize = Number(params.pageSize || 20)
  const start = (page - 1) * pageSize
  return { list: list.slice(start, start + pageSize), total: list.length }
}

function filterByKeyword(list, keyword, fields) {
  if (!keyword || keyword.length < 2) return list
  const lower = String(keyword).toLowerCase()
  return list.filter(item => fields.some(field => String(item[field] || '').toLowerCase().includes(lower)))
}

function getParams(config) {
  return config.params || {}
}

function getBody(config) {
  const body = config.data
  if (!body) return {}
  if (typeof body === 'string') {
    try { return JSON.parse(body) } catch { return {} }
  }
  return body
}

export async function mockAdapter(config) {
  const url = config.url || ''
  const method = (config.method || 'get').toLowerCase()
  const params = getParams(config)
  const body = getBody(config)
  let data = {}

  if (url === '/api/auth/login' || url === '/api/auth/login/sms') {
    data = ok({ accessToken: makeToken(), refreshToken: makeToken() })
  } else if (url === '/api/auth/sms/send') {
    data = ok({ sent: true })
  } else if (url === '/api/auth/register' && method === 'post') {
    user.username = body.username || user.username
    user.nickname = body.nickname || body.username || user.nickname
    user.real_name = body.real_name || user.real_name
    user.phone = body.phone || user.phone
    data = ok({ accessToken: makeToken(), refreshToken: makeToken() })
  } else if (url === '/api/auth/userinfo') {
    data = ok(user)
  } else if (url === '/api/user/avatar' && method === 'post') {
    user.avatar_url = body.avatar_url || body.url || user.avatar_url
    data = ok({ avatar_url: user.avatar_url })
  } else if (url === '/api/home/banners') {
    data = ok([
      { id: 1, title: '选课周提醒', image_url: svgData('选课周提醒', '#e87452', '#f4b24f'), link_url: '/course' },
      { id: 2, title: '资料共享计划', image_url: svgData('资料共享计划', '#c98272', '#e87452'), link_url: '/material' },
      { id: 3, title: '积分兑换上新', image_url: svgData('积分兑换上新', '#86a96f', '#d89334'), link_url: '/mall' }
    ])
  } else if (url === '/api/home/news') {
    const type = params.type
    const news = [
      { id: 1, type: 'notice', title: '2026 春季补退选通道今晚开放', summary: '课程容量和时间冲突已同步更新，建议先收藏备选课程。', publish_time: daysAgo(0), link_url: '/course' },
      { id: 2, type: 'info', title: '资料区新增 42 份期末复习材料', summary: '覆盖算法、数据库、前端工程等课程，可按热度排序查看。', publish_time: daysAgo(1), link_url: '/material' },
      { id: 3, type: 'notice', title: '软件工程实践答辩安排发布', summary: '请各小组在周五前确认答辩时段和展示设备需求。', publish_time: daysAgo(2), link_url: '/course/5' }
    ].filter(item => !type || item.type === type)
    data = ok(paginate(news, params))
  } else if (url === '/api/home/rank') {
    data = ok({ list: [
      { user_id: 1008, nickname: '李欣', score: 920 },
      { user_id: 1001, nickname: '张义彬', score: 820 },
      { user_id: 1016, nickname: '周可', score: 775 },
      { user_id: 1022, nickname: '林澈', score: 708 },
      { user_id: 1033, nickname: '韩予', score: 681 }
    ]})
  } else if (url === '/api/home/order/count') {
    data = ok({ count: orders.filter(order => order.status === 1).length })
  } else if (url === '/api/course/categories') {
    data = ok(categories)
  } else if (url === '/api/course/list' || url === '/api/course/search') {
    let list = courses.filter(item => !params.categoryId || item.category_id === Number(params.categoryId))
    list = list.filter(item => !params.courseType || item.course_type === Number(params.courseType))
    list = list.filter(item => !params.creditMin || item.credit >= Number(params.creditMin))
    list = list.filter(item => !params.teacherName || item.teacher_name === params.teacherName)
    list = filterByKeyword(list, params.keyword, ['course_name', 'course_code', 'teacher_name'])
    data = ok(paginate(list, params))
  } else if (/^\/api\/course\/\d+\/review/.test(url) && method === 'post') {
    const id = Number(url.split('/')[3])
    const course = courses.find(item => item.course_id === id)
    const review = {
      review_id: Date.now(),
      user_name: user.real_name || user.nickname,
      rating: Number(body.rating || 5),
      content: String(body.content || ''),
      create_time: new Date().toISOString()
    }
    if (course) {
      course.reviews = [review, ...(course.reviews || [])]
      const total = course.reviews.reduce((sum, item) => sum + Number(item.rating || 0), 0)
      course.avg_rating = Number((total / course.reviews.length).toFixed(1))
    }
    user.score_balance += 3
    user.total_score += 3
    scoreDetails.unshift({ id: Date.now(), source: '课程评价奖励', amount: 3, create_time: new Date().toISOString() })
    data = ok({ review, avg_rating: course?.avg_rating || review.rating, score_balance: user.score_balance })
  } else if (/^\/api\/course\/\d+/.test(url)) {
    const id = Number(url.split('/').pop())
    const course = courses.find(item => item.course_id === id)
    data = ok(course ? { ...course, related_courses: courses.filter(item => item.course_id !== id).slice(0, 6) } : null)
  } else if (url === '/api/material/categories') {
    data = ok(categories)
  } else if (url === '/api/material/upload' && method === 'post') {
    const material = {
      material_id: materials.length + 1,
      material_name: body.material_name || body.file_name || '新上传资料',
      material_type: Number(body.material_type || 1),
      uploader_name: user.real_name || user.nickname,
      download_count: 0,
      download_score: Number(body.download_score || 0),
      course_id: Number(body.course_id || 1),
      create_time: new Date().toISOString(),
      file_url: body.file_url || 'about:blank',
      is_favorited: false,
      keyword: body.keyword || ''
    }
    materials.unshift(material)
    user.score_balance += 2
    user.total_score += 2
    scoreDetails.unshift({ id: Date.now(), source: '分享资料奖励', amount: 2, create_time: new Date().toISOString() })
    data = ok({ material, score_balance: user.score_balance })
  } else if (url === '/api/material/list') {
    let list = materials.filter(item => !params.materialType || item.material_type === Number(params.materialType))
    list = list.filter(item => !params.courseId || item.course_id === Number(params.courseId))
    list = filterByKeyword(list, params.keyword, ['material_name', 'uploader_name', 'keyword'])
    data = ok(paginate(list, params))
  } else if (/^\/api\/material\/\d+\/download/.test(url)) {
    const id = Number(url.split('/')[3])
    const material = materials.find(item => item.material_id === id)
    data = ok({ download_url: material?.file_url || 'about:blank', score_balance: user.score_balance - (material?.download_score || 0) })
  } else if (/^\/api\/material\/\d+\/report/.test(url) && method === 'post') {
    data = ok({ report_id: Date.now(), status: 'submitted', reason: body.reason || 'other' })
  } else if (/^\/api\/material\/\d+/.test(url)) {
    const id = Number(url.split('/').pop())
    data = ok(materials.find(item => item.material_id === id) || null)
  } else if (url === '/api/contact/list' || url === '/api/contact/search') {
    const isTeacher = params.type !== 'student'
    let list = contacts.filter(item => isTeacher ? item.user_type === 2 : item.user_type === 1)
    list = filterByKeyword(list, params.keyword, ['real_name', 'student_id', 'department'])
    data = ok({ list, total: list.length })
  } else if (/^\/api\/contact\/\d+/.test(url)) {
    const id = Number(url.split('/').pop())
    data = ok(contacts.find(item => item.user_id === id) || null)
  } else if (url === '/api/mall/goods') {
    let list = goods.filter(item => !params.categoryId || item.category_id === Number(params.categoryId))
    data = ok(paginate(list, params))
  } else if (/^\/api\/mall\/goods\/\d+/.test(url)) {
    const id = Number(url.split('/').pop())
    data = ok(goods.find(item => item.goods_id === id) || null)
  } else if (url === '/api/mall/exchanges') {
    data = ok({ list: [{ exchange_id: 1, status: 2, exchange_time: daysAgo(1) }] })
  } else if (url === '/api/mall/exchange' && method === 'post') {
    data = ok({ score_balance: 266 })
  } else if (url === '/api/favorite/list') {
    data = ok({ list: params.type === 'material' ? materials.filter(item => item.is_favorited) : courses.filter(item => item.is_favorited) })
  } else if (url === '/api/favorite/add' || url === '/api/favorite/remove') {
    data = ok({ success: true })
  } else if (url === '/api/order/list') {
    const list = orders.filter(item => !params.status || item.status === Number(params.status))
    data = ok(paginate(list, params))
  } else if (/^\/api\/order\/\d+\/cancel/.test(url)) {
    data = ok({ success: true })
  } else if (/^\/api\/order\/\d+/.test(url)) {
    const id = Number(url.split('/').pop())
    data = ok(orders.find(item => item.order_id === id) || null)
  } else if (url === '/api/score/balance') {
    data = ok({ balance: user.score_balance, total_score: user.total_score })
  } else if (url === '/api/score/details') {
    data = ok(paginate(scoreDetails, params))
  } else {
    data = ok({})
  }

  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config
  }
}
