import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { accountApi, getErrorMessage } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { ACCOUNT_TYPE_LABEL, STATUS_BADGE, formatDate, formatMoney } from '../utils/format'

export default function Dashboard() {
  const { user } = useAuth()
  const location = useLocation()

  const [accounts, setAccounts] = useState(null)
  const [error, setError] = useState('')
  const [openType, setOpenType] = useState('SAVINGS')
  const [opening, setOpening] = useState(false)
  const [notice, setNotice] = useState(location.state?.welcome
    ? 'Welcome! Open your first account below. Our staff will approve it shortly.' : '')

  const load = () => accountApi.mine().then(setAccounts).catch(async (err) => setError(await getErrorMessage(err)))

  useEffect(() => {
    load()
  }, [])

  const handleOpen = async (e) => {
    e.preventDefault()
    setOpening(true)
    setError('')
    try {
      await accountApi.open(openType)
      setNotice(`Your ${ACCOUNT_TYPE_LABEL[openType]} account request was sent. It will be usable once approved.`)
      await load()
    } catch (err) {
      setError(await getErrorMessage(err))
    } finally {
      setOpening(false)
    }
  }

  if (!accounts && !error) return <p className="center muted">Loading your accounts…</p>

  const active = (accounts || []).filter((a) => a.status === 'ACTIVE')
  const total = active.reduce((sum, a) => sum + Number(a.balance), 0)

  return (
    <>
      <section className="hero-card">
        <div>
          <p className="hero-label">Hello, {user.name.split(' ')[0]} 👋</p>
          <p className="hero-label">Total balance across active accounts</p>
          <p className="hero-amount">{formatMoney(total)}</p>
        </div>
        <div className="row-gap">
          <Link to="/transfer" className="btn btn-light">Send money</Link>
          <Link to="/beneficiaries" className="btn btn-outline-light">Beneficiaries</Link>
        </div>
      </section>

      {notice && <div className="alert alert-success">{notice}</div>}
      {error && <div className="alert alert-error" role="alert">{error}</div>}

      <div className="page-header">
        <h2>Your accounts</h2>
      </div>

      {accounts?.length === 0 && <div className="empty"><p>You don't have any accounts yet.</p></div>}

      <div className="account-grid">
        {accounts?.map((a) => {
          const usable = a.status === 'ACTIVE' || a.status === 'FROZEN'
          const body = (
            <>
              <div className="account-top">
                <span className="account-type">{ACCOUNT_TYPE_LABEL[a.type]} account</span>
                <span className={`badge ${STATUS_BADGE[a.status]}`}>{a.status}</span>
              </div>
              <p className="account-number">{a.accountNumber || 'Number given on approval'}</p>
              <p className="account-balance">{formatMoney(a.balance)}</p>
              <p className="muted small">
                {a.status === 'PENDING' && `Requested on ${formatDate(a.createdAt)}. Waiting for staff approval.`}
                {a.status === 'ACTIVE' && 'View history, deposit, withdraw or download statements →'}
                {a.status === 'FROZEN' && 'Frozen by the bank. Contact the branch for help.'}
                {a.status === 'REJECTED' && 'This request was not approved.'}
              </p>
            </>
          )
          return usable
            ? <Link key={a.id} to={`/accounts/${a.id}`} className="card account-card clickable">{body}</Link>
            : <div key={a.id} className="card account-card">{body}</div>
        })}

        <form className="card account-card open-card" onSubmit={handleOpen}>
          <span className="account-type">Open a new account</span>
          <label className="field">
            <span>Account type</span>
            <select value={openType} onChange={(e) => setOpenType(e.target.value)}>
              <option value="SAVINGS">Savings: for personal savings</option>
              <option value="CURRENT">Current: for frequent transactions</option>
            </select>
          </label>
          <button className="btn btn-primary" disabled={opening}>{opening ? 'Sending request…' : 'Request account'}</button>
        </form>
      </div>
    </>
  )
}
