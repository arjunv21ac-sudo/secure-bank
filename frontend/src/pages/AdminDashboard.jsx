import { useCallback, useEffect, useState } from 'react'
import { adminApi, getErrorMessage } from '../api/client'
import { ACCOUNT_TYPE_LABEL, STATUS_BADGE, formatDate, formatMoney } from '../utils/format'

const TABS = [
  { key: 'PENDING', label: 'Pending approvals' },
  { key: 'ACCOUNTS', label: 'All accounts' },
  { key: 'CUSTOMERS', label: 'Customers' },
]

export default function AdminDashboard() {
  const [stats, setStats] = useState(null)
  const [tab, setTab] = useState('PENDING')
  const [rows, setRows] = useState(null)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState(null)

  const loadStats = () => adminApi.dashboard().then(setStats).catch(async (err) => setError(await getErrorMessage(err)))

  const loadRows = useCallback(() => {
    setRows(null)
    const request = tab === 'CUSTOMERS' ? adminApi.customers()
      : adminApi.accounts(tab === 'PENDING' ? 'PENDING' : null)
    return request.then(setRows).catch(async (err) => setError(await getErrorMessage(err)))
  }, [tab])

  useEffect(() => {
    loadStats()
  }, [])

  useEffect(() => {
    loadRows()
  }, [loadRows])

  // Runs an admin action, then refreshes the numbers and the table
  const act = async (id, action, confirmText) => {
    if (confirmText && !window.confirm(confirmText)) return
    setBusyId(id)
    setError('')
    try {
      await action(id)
      await Promise.all([loadStats(), loadRows()])
    } catch (err) {
      setError(await getErrorMessage(err))
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <div className="page-header"><h1>Admin panel</h1></div>
      {error && <div className="alert alert-error" role="alert">{error}</div>}

      {stats && (
        <div className="stat-grid">
          <div className="card stat"><span className="muted">Customers</span><strong>{stats.customers}</strong></div>
          <div className="card stat"><span className="muted">Active accounts</span><strong>{stats.activeAccounts}</strong></div>
          <div className="card stat highlight"><span className="muted">Pending approval</span><strong>{stats.pendingAccounts}</strong></div>
          <div className="card stat"><span className="muted">Frozen</span><strong>{stats.frozenAccounts}</strong></div>
          <div className="card stat"><span className="muted">Total deposits</span><strong>{formatMoney(stats.totalDeposits)}</strong></div>
          <div className="card stat"><span className="muted">Transfers today</span><strong>{stats.transfersToday} · {formatMoney(stats.transferredToday)}</strong></div>
        </div>
      )}

      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t.key} role="tab" aria-selected={tab === t.key}
                  className={`tab ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>
            {t.label}
          </button>
        ))}
      </div>

      <div className="card table-wrap">
        {!rows && <p className="muted pad">Loading…</p>}
        {rows?.length === 0 && <p className="muted pad">{tab === 'PENDING' ? 'No accounts waiting for approval 🎉' : 'Nothing here yet.'}</p>}

        {rows?.length > 0 && tab !== 'CUSTOMERS' && (
          <table className="table">
            <thead>
              <tr><th>Customer</th><th>Type</th><th>Account no.</th><th>Status</th><th className="num">Balance</th><th>Requested</th><th aria-label="Actions" /></tr>
            </thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id}>
                  <td><strong>{a.ownerName}</strong><div className="muted small">{a.ownerEmail}</div></td>
                  <td>{ACCOUNT_TYPE_LABEL[a.type]}</td>
                  <td className="nowrap">{a.accountNumber || '—'}</td>
                  <td><span className={`badge ${STATUS_BADGE[a.status]}`}>{a.status}</span></td>
                  <td className="num nowrap">{formatMoney(a.balance)}</td>
                  <td className="nowrap">{formatDate(a.createdAt)}</td>
                  <td className="actions">
                    {a.status === 'PENDING' && (
                      <>
                        <button className="btn btn-primary btn-sm" disabled={busyId === a.id} onClick={() => act(a.id, adminApi.approve)}>Approve</button>
                        <button className="btn btn-danger-outline btn-sm" disabled={busyId === a.id}
                                onClick={() => act(a.id, adminApi.reject, `Reject ${a.ownerName}'s account request?`)}>Reject</button>
                      </>
                    )}
                    {a.status === 'ACTIVE' && (
                      <button className="btn btn-danger-outline btn-sm" disabled={busyId === a.id}
                              onClick={() => act(a.id, adminApi.freeze, `Freeze ${a.accountNumber}? No money can move until it is unfrozen.`)}>Freeze</button>
                    )}
                    {a.status === 'FROZEN' && (
                      <button className="btn btn-ghost btn-sm" disabled={busyId === a.id} onClick={() => act(a.id, adminApi.unfreeze)}>Unfreeze</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {rows?.length > 0 && tab === 'CUSTOMERS' && (
          <table className="table">
            <thead>
              <tr><th>Name</th><th>Email</th><th>Joined</th><th>Login</th><th aria-label="Actions" /></tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  <td><strong>{c.name}</strong></td>
                  <td>{c.email}</td>
                  <td className="nowrap">{formatDate(c.createdAt)}</td>
                  <td>
                    {c.locked
                      ? <span className="badge badge-danger">Locked</span>
                      : <span className="badge badge-success">OK</span>}
                    {!c.locked && c.failedLoginAttempts > 0 && <div className="muted small">{c.failedLoginAttempts} failed attempt(s)</div>}
                  </td>
                  <td className="actions">
                    {c.locked && (
                      <button className="btn btn-primary btn-sm" disabled={busyId === c.id} onClick={() => act(c.id, adminApi.unlock)}>Unlock</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  )
}
