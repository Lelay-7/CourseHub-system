import request from './request'
const data = promise => promise.then(result => result.data)
export const openPrivate = (targetUserId,config={}) => data(request.post('/api/chat/conversations/private',{target_user_id:targetUserId},config))
export const listConversations = (params={},config={}) => data(request.get('/api/chat/conversations',{...config,params}))
export const getConversation = (id,config={}) => data(request.get(`/api/chat/conversations/${id}`,config))
export const getMessages = (id,params={},config={}) => data(request.get(`/api/chat/conversations/${id}/messages`,{...config,params}))
export const sendMessage = (id,body,config={}) => data(request.post(`/api/chat/conversations/${id}/messages`,body,config))
export const markRead = (id,sequence,config={}) => data(request.put(`/api/chat/conversations/${id}/read`,{last_read_sequence:sequence},config))
export const getUnread = (config={}) => data(request.get('/api/chat/unread',config))
