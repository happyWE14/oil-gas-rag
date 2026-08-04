const withoutTrailingSlash = (value) => (value || '').replace(/\/$/, '')

export const API_ORIGIN = withoutTrailingSlash(import.meta.env.VITE_API_ORIGIN)
export const API_BASE_URL = `${API_ORIGIN}/api`
export const FILE_API_BASE_URL = `${API_BASE_URL}/file`
export const DEFAULT_PAPER_LOCAL_PATH = import.meta.env.VITE_PAPER_LOCAL_PATH || ''
