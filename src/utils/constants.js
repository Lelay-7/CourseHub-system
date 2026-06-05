/**
 * 系统常量定义
 * 集中管理枚举值映射
 */

/** 用户角色 */
export const USER_TYPE = {
  STUDENT: 1,
  TEACHER: 2,
  ADMIN: 3
}
export const USER_TYPE_LABEL = {
  [USER_TYPE.STUDENT]: '学生',
  [USER_TYPE.TEACHER]: '教师',
  [USER_TYPE.ADMIN]: '管理员'
}

/** 用户状态 */
export const USER_STATUS = {
  NORMAL: 1,
  DISABLED: 0
}

/** 课程类型 */
export const COURSE_TYPE = {
  REQUIRED: 1,
  ELECTIVE: 2
}
export const COURSE_TYPE_LABEL = {
  [COURSE_TYPE.REQUIRED]: '必修',
  [COURSE_TYPE.ELECTIVE]: '选修'
}

/** 课程状态 */
export const COURSE_STATUS = {
  ACTIVE: 1,
  ENDED: 0
}
export const COURSE_STATUS_LABEL = {
  [COURSE_STATUS.ACTIVE]: '在选',
  [COURSE_STATUS.ENDED]: '已结束'
}

/** 资料类型 */
export const MATERIAL_TYPE = {
  PDF: 1,
  WORD: 2,
  EXCEL: 3,
  PPT: 4,
  IMAGE: 5,
  ARCHIVE: 6
}
export const MATERIAL_TYPE_LABEL = {
  [MATERIAL_TYPE.PDF]: 'PDF',
  [MATERIAL_TYPE.WORD]: 'Word',
  [MATERIAL_TYPE.EXCEL]: 'Excel',
  [MATERIAL_TYPE.PPT]: 'PPT',
  [MATERIAL_TYPE.IMAGE]: '图片',
  [MATERIAL_TYPE.ARCHIVE]: '压缩包'
}
export const MATERIAL_TYPE_ICON = {
  [MATERIAL_TYPE.PDF]: 'Document',
  [MATERIAL_TYPE.WORD]: 'Document',
  [MATERIAL_TYPE.EXCEL]: 'Grid',
  [MATERIAL_TYPE.PPT]: 'DataAnalysis',
  [MATERIAL_TYPE.IMAGE]: 'Picture',
  [MATERIAL_TYPE.ARCHIVE]: 'FolderOpened'
}

/** 资料状态 */
export const MATERIAL_STATUS = {
  PENDING: 0,
  PUBLISHED: 1,
  REMOVED: 2
}

/** 订单状态 */
export const ORDER_STATUS = {
  PENDING_PAY: 1,
  PAID: 2,
  COMPLETED: 3,
  CANCELLED: 4
}
export const ORDER_STATUS_LABEL = {
  [ORDER_STATUS.PENDING_PAY]: '待支付',
  [ORDER_STATUS.PAID]: '已支付',
  [ORDER_STATUS.COMPLETED]: '已完成',
  [ORDER_STATUS.CANCELLED]: '已取消'
}

/** 商品类型 */
export const GOODS_TYPE = {
  PHYSICAL: 1,
  VIRTUAL: 2,
  SERVICE: 3
}
export const GOODS_TYPE_LABEL = {
  [GOODS_TYPE.PHYSICAL]: '实物',
  [GOODS_TYPE.VIRTUAL]: '虚拟',
  [GOODS_TYPE.SERVICE]: '服务'
}

/** 兑换状态 */
export const EXCHANGE_STATUS = {
  PENDING_SHIP: 1,
  SHIPPED: 2,
  COMPLETED: 3
}
export const EXCHANGE_STATUS_LABEL = {
  [EXCHANGE_STATUS.PENDING_SHIP]: '待发货',
  [EXCHANGE_STATUS.SHIPPED]: '已发货',
  [EXCHANGE_STATUS.COMPLETED]: '已完成'
}

/** 积分筛选区间 */
export const SCORE_RANGE = [
  { label: '0-50分', min: 0, max: 50 },
  { label: '50-200分', min: 50, max: 200 },
  { label: '200分以上', min: 200, max: Infinity }
]

/** 资料上传时间筛选 */
export const MATERIAL_TIME_FILTER = [
  { label: '7天内', value: 7 },
  { label: '30天内', value: 30 },
  { label: '全部', value: 0 }
]

/** 学分范围 */
export const CREDIT_RANGE = { min: 1, max: 8 }

/** 分页默认值 */
export const PAGE_SIZE = 20
export const COMMENT_PAGE_SIZE = 15

/** 头像大小限制 */
export const AVATAR_MAX_SIZE = 2 * 1024 * 1024 // 2MB

/** 资料预览大小限制 */
export const PREVIEW_MAX_SIZE = 20 * 1024 * 1024 // 20MB
