import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { getErrorMessage } from '../api/client'
import { useAuth } from '../context/AuthContext'

export default function Login() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()

  const [form, setForm] = useState({ email: '', password: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/" replace />

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSubmitting(true)
    setError('')
    try {
      const loggedIn = await login(form)
      const home = loggedIn.role === 'ADMIN' ? '/admin' : '/dashboard'
      navigate(location.state?.from || home, { replace: true })
    } catch (err) {
      setError(await getErrorMessage(err))
      setForm((f) => ({ ...f, password: '' }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>Login to SecureBank</h1>
        <p className="muted">Manage your accounts, transfers and statements.</p>

        {searchParams.get('expired') && (
          <div className="alert alert-info">For your security, sessions end after 30 minutes. Please login again.</div>
        )}
        {error && <div className="alert alert-error" role="alert">{error}</div>}

        <label className="field">
          <span>Email</span>
          <input type="email" name="email" value={form.email} onChange={handleChange} required autoComplete="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" name="password" value={form.password} onChange={handleChange} required autoComplete="current-password" />
        </label>

        <button className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Checking…' : 'Login'}
        </button>
        <p className="center muted small">After 3 wrong passwords your login is locked until the bank unlocks it.</p>
        <p className="center muted">New customer? <Link to="/register">Open an account online</Link></p>
      </form>
    </div>
  )
}
