import request from './request'

export const getSquarePosts = (params) => request.get('/api/square', { params })
export const createSquarePost = (data) => request.post('/api/square', data)
export const deleteSquarePost = (id) => request.delete(`/api/square/${id}`)
export const getComments = (postId) => request.get(`/api/square/${postId}/comments`)
export const addComment = (postId, data) => request.post(`/api/square/${postId}/comments`, data)
export const deleteComment = (commentId) => request.delete(`/api/square/comments/${commentId}`)
export const toggleLike = (postId) => request.post(`/api/square/${postId}/like`)
