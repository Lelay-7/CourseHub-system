// Use the existing feedback workflow unless a real support address is configured.
export const supportPath = '/profile/support'
export const feedbackPath = '/profile/feedback'
export const supportEmail = (import.meta.env.VITE_SUPPORT_EMAIL || '').trim()
export const appVersion = import.meta.env.VITE_APP_VERSION || '3.0.0'
