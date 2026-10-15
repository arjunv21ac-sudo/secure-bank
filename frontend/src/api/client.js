import axios from 'axios'

export const AUTH_STORAGE_KEY = 'securebank_auth'

// In dev, Vite proxies /api to Spring Boot. In production, set VITE_API_URL.
export const API_BASE = import.meta.env.VITE_API_URL || '/api'

const api = axios.create({ baseURL: API_BASE })

// Attach the JWT to every request
api.interceptors.request.use((config) => {
  const saved = localStorage.getItem(AUTH_STORAGE_KEY)
  if (saved) {
    const { token } = JSON.parse(saved)
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Token expired (sessions last 30 minutes) or invalid: log out and go to the login page
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const isAuthCall = error.config?.url?.startsWith('/auth')
    if (error.response?.status === 401 && !isAuthCall) {
      localStorage.removeItem(AUTH_STORAGE_KEY)
      window.location.href = '/login?expired=1'
    }
    return Promise.reject(error)
  },
)

const data = (response) => response.data

export const authApi = {
  login: (credentials) => api.post('/auth/login', credentials).then(data),
  register: (form) => api.post('/auth/register', form).then(data),
}

export const accountApi = {
  mine: () => api.get('/accounts').then(data),
  open: (type) => api.post('/accounts', { type }).then(data),
  get: (id) => api.get(`/accounts/${id}`).then(data),
  lookup: (accountNumber) => api.get(`/accounts/lookup/${encodeURIComponent(accountNumber.trim())}`).then(data),
  deposit: (id, amount, description) => api.post(`/accounts/${id}/deposit`, { amount, description }).then(data),
  withdraw: (id, amount, description) => api.post(`/accounts/${id}/withdraw`, { amount, description }).then(data),
  history: (id, { from, to, page = 0, size = 10 }) =>
    api.get(`/accounts/${id}/transactions`, { params: { from: from || undefined, to: to || undefined, page, size } }).then(data),
  // Returns the raw response so the caller can save the PDF blob
  statement: (id, month) => api.get(`/accounts/${id}/statement`, { params: { month }, responseType: 'blob' }),
}

export const transferApi = {
  // The same idempotencyKey on a retry returns the original transfer instead of paying twice
  send: (transfer, idempotencyKey) =>
    api.post('/transfers', transfer, { headers: { 'Idempotency-Key': idempotencyKey } }).then(data),
}

export const beneficiaryApi = {
  list: () => api.get('/beneficiaries').then(data),
  add: (nickname, accountNumber) => api.post('/beneficiaries', { nickname, accountNumber }).then(data),
  remove: (id) => api.delete(`/beneficiaries/${id}`),
}

export const adminApi = {
  dashboard: () => api.get('/admin/dashboard').then(data),
  accounts: (status) => api.get('/admin/accounts', { params: { status: status || undefined } }).then(data),
  approve: (id) => api.post(`/admin/accounts/${id}/approve`).then(data),
  reject: (id) => api.post(`/admin/accounts/${id}/reject`).then(data),
  freeze: (id) => api.post(`/admin/accounts/${id}/freeze`).then(data),
  unfreeze: (id) => api.post(`/admin/accounts/${id}/unfreeze`).then(data),
  customers: () => api.get('/admin/customers').then(data),
  unlock: (id) => api.post(`/admin/customers/${id}/unlock`).then(data),
}

// Turns any API error into one readable sentence for the UI
export async function getErrorMessage(error) {
  let body = error.response?.data
  // Downloads (responseType 'blob') return errors as a Blob that must be read first
  if (body instanceof Blob) {
    try {
      body = JSON.parse(await body.text())
    } catch {
      body = null
    }
  }
  if (body?.fieldErrors) return Object.values(body.fieldErrors).join('. ')
  if (body?.message) return body.message
  if (!error.response) return 'Could not reach the bank server. Please check your connection and try again.'
  return 'Something went wrong. Please try again.'
}
