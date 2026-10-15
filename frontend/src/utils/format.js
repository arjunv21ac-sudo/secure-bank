const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 })

// 21950.5 -> "₹21,950.50" (Indian digit grouping: ₹1,00,000.00)
export function formatMoney(amount) {
  return rupees.format(Number(amount ?? 0))
}

// "15 Oct 2026, 1:33 pm"
export function formatDateTime(iso) {
  return new Date(iso).toLocaleString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

// "2026-10" for <input type="month">
export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export const ACCOUNT_TYPE_LABEL = { SAVINGS: 'Savings', CURRENT: 'Current' }

export const STATUS_BADGE = {
  ACTIVE: 'badge-success',
  PENDING: 'badge-warning',
  FROZEN: 'badge-info',
  REJECTED: 'badge-danger',
}

// Allows only digits with up to 2 decimals while typing an amount
export function isValidAmountInput(value) {
  return value === '' || /^\d{0,13}(\.\d{0,2})?$/.test(value)
}
