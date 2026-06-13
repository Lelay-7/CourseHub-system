/**
 * 表单校验规则集
 * 供 Element Plus el-form 使用
 */

/**
 * 昵称校验：2-20字符，禁止特殊字符
 */
export const validateNickname = (rule, value, callback) => {
  if (!value || value.trim() === '') {
    callback(new Error('昵称不能为空'))
  } else if (value.length < 2 || value.length > 20) {
    callback(new Error('昵称长度为2-20个字符'))
  } else if (!/^[\w一-鿿]+$/.test(value)) {
    callback(new Error('昵称不能包含特殊字符'))
  } else {
    callback()
  }
}

/**
 * 手机号校验：中国大陆手机号格式
 */
export const validatePhone = (rule, value, callback) => {
  if (!value || value.trim() === '') {
    callback(new Error('手机号不能为空'))
  } else if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

/**
 * 搜索关键词校验：≥2字符
 */
export const validateSearchKeyword = (rule, value, callback) => {
  if (value && value.length < 2) {
    callback(new Error('请输入至少2个字符'))
  } else {
    callback()
  }
}

/**
 * 密码校验：6-20字符
 */
export const validatePassword = (rule, value, callback) => {
  if (!value || value.trim() === '') {
    callback(new Error('密码不能为空'))
  } else if (value.length < 6 || value.length > 20) {
    callback(new Error('密码长度为6-20个字符'))
  } else {
    callback()
  }
}

/**
 * 验证码校验：6位数字
 */
export const validateSmsCode = (rule, value, callback) => {
  if (!value || value.trim() === '') {
    callback(new Error('验证码不能为空'))
  } else if (!/^\d{6}$/.test(value)) {
    callback(new Error('请输入6位数字验证码'))
  } else {
    callback()
  }
}

/**
 * 常用 Element Plus form rules 预设
 */
export const formRules = {
  nickname: [
    { required: true, validator: validateNickname, trigger: 'blur' }
  ],
  phone: [
    { required: true, validator: validatePhone, trigger: 'blur' }
  ],
  password: [
    { required: true, validator: validatePassword, trigger: 'blur' }
  ],
  smsCode: [
    { required: true, validator: validateSmsCode, trigger: 'blur' }
  ],
  searchKeyword: [
    { validator: validateSearchKeyword, trigger: 'blur' }
  ]
}
