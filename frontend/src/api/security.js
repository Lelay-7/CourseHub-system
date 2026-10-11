import request from './request'

export const getSecurity = () => request.get('/api/user/security')
export const sendBindingCode = data => request.post('/api/user/security/email/code', data)
export const bindEmail = data => request.put('/api/user/security/email', data)
export const changePassword = data => request.put('/api/user/security/password', data)
export const sendResetCode = data => request.post('/api/auth/password-reset/code', data)
export const resetPassword = data => request.post('/api/auth/password-reset', data)
