import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/client'
import { useAuth } from '../context/AuthContext'

export default function Register() {
  const { user, register } = useAuth()
  const navigate = useNavigate()

  const [form, setForm] = useState({ name: '', email: '', password: '', confirm: '' })
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/" replace />

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (form.password !== form.confirm) {
      setError('Passwords do not match')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await register({ name: form.name, email: form.email, password: form.password })
      navigate('/dashboard', { replace: true, state: { welcome: true } })
    } catch (err) {
      setError(await getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>Become a customer</h1>
        <p className="muted">Register, then open a Savings or Current account. Our staff approve new accounts.</p>

        {error && <div className="alert alert-error" role="alert">{error}</div>}

        <label className="field">
          <span>Full name</span>
          <input name="name" value={form.name} onChange={handleChange} required maxLength={100} autoComplete="name" />
        </label>
        <label className="field">
          <span>Email</span>
          <input type="email" name="email" value={form.email} onChange={handleChange} required autoComplete="email" />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" name="password" value={form.password} onChange={handleChange} required minLength={8}
                 pattern="(?=.*[A-Za-z])(?=.*\d).{8,}" title="At least 8 characters with letters and numbers" autoComplete="new-password" />
          <small className="muted">At least 8 characters, with letters and numbers</small>
        </label>
        <label className="field">
          <span>Confirm password</span>
          <input type="password" name="confirm" value={form.confirm} onChange={handleChange} required autoComplete="new-password" />
        </label>

        <button className="btn btn-primary btn-block" disabled={submitting}>
          {submitting ? 'Creating your profile…' : 'Register'}
        </button>
        <p className="center muted">Already a customer? <Link to="/login">Login</Link></p>
      </form>
    </div>
  )
}
