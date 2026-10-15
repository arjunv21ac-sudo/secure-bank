import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { accountApi, getErrorMessage } from '../api/client'
import { ACCOUNT_TYPE_LABEL, STATUS_BADGE, currentMonth, formatDateTime, formatMoney, isValidAmountInput } from '../utils/format'

const PAGE_SIZE = 10

export default function AccountDetails() {
  const { id } = useParams()

  const [account, setAccount] = useState(null)
  const [error, setError] = useState('')

  // Deposit / withdraw panel
  const [mode, setMode] = useState('DEPOSIT')
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const [moving, setMoving] = useState(false)
  const [moneyMessage, setMoneyMessage] = useState(null) // { ok, text }

  // History
  const [filters, setFilters] = useState({ from: '', to: '' })
  const [applied, setApplied] = useState({ from: '', to: '' })
  const [page, setPage] = useState(0)
  const [history, setHistory] = useState(null)

  // Statement
  const [month, setMonth] = useState(currentMonth())
  const [downloading, setDownloading] = useState(false)
  const [statementError, setStatementError] = useState('')

  const loadAccount = useCallback(
    () => accountApi.get(id).then(setAccount).catch(async (err) => setError(await getErrorMessage(err))),
    [id],
  )

  const loadHistory = useCallback(
    () => accountApi.history(id, { ...applied, page, size: PAGE_SIZE })
      .then(setHistory)
      .catch(async (err) => setError(await getErrorMessage(err))),
    [id, applied, page],
  )

  useEffect(() => {
    loadAccount()
  }, [loadAccount])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  const handleMoney = async (e) => {
    e.preventDefault()
    setMoving(true)
    setMoneyMessage(null)
    try {
      const call = mode === 'DEPOSIT' ? accountApi.deposit : accountApi.withdraw
      const txn = await call(id, amount, note)
      setMoneyMessage({ ok: true, text: `${mode === 'DEPOSIT' ? 'Deposited' : 'Withdrew'} ${formatMoney(txn.amount)}. New balance ${formatMoney(txn.balanceAfter)}.` })
      setAmount('')
      setNote('')
      await Promise.all([loadAccount(), page === 0 ? loadHistory() : Promise.resolve(setPage(0))])
    } catch (err) {
      setMoneyMessage({ ok: false, text: await getErrorMessage(err) })
    } finally {
      setMoving(false)
    }
  }

  const handleFilter = (e) => {
    e.preventDefault()
    setPage(0)
    setApplied({ ...filters })
  }

  const clearFilter = () => {
    setFilters({ from: '', to: '' })
    setApplied({ from: '', to: '' })
    setPage(0)
  }

  const downloadStatement = async () => {
    setDownloading(true)
    setStatementError('')
    try {
      const response = await accountApi.statement(id, month)
      const url = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      link.download = `SecureBank-${account.accountNumber}-${month}.pdf`
      link.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      setStatementError(await getErrorMessage(err))
    } finally {
      setDownloading(false)
    }
  }

  if (error) return <div className="alert alert-error">{error} <Link to="/dashboard">Back to accounts</Link></div>
  if (!account) return <p className="center muted">Loading account…</p>

  const active = account.status === 'ACTIVE'

  return (
    <>
      <Link to="/dashboard" className="muted small">← All accounts</Link>

      <section className="hero-card">
        <div>
          <p className="hero-label">{ACCOUNT_TYPE_LABEL[account.type]} account · {account.accountNumber}</p>
          <p className="hero-amount">{formatMoney(account.balance)}</p>
          <span className={`badge ${STATUS_BADGE[account.status]}`}>{account.status}</span>
        </div>
        {active && <Link to={`/transfer?from=${account.id}`} className="btn btn-light">Send money</Link>}
      </section>

      {!active && <div className="alert alert-info">This account is {account.status.toLowerCase()}. Money can't move in or out right now.</div>}

      <div className="two-col">
        <form className="card" onSubmit={handleMoney}>
          <div className="tabs" role="tablist">
            {['DEPOSIT', 'WITHDRAW'].map((m) => (
              <button key={m} type="button" role="tab" aria-selected={mode === m}
                      className={`tab ${mode === m ? 'active' : ''}`}
                      onClick={() => { setMode(m); setMoneyMessage(null) }}>
                {m === 'DEPOSIT' ? 'Deposit' : 'Withdraw'}
              </button>
            ))}
          </div>
          <label className="field">
            <span>Amount (₹)</span>
            <input inputMode="decimal" value={amount} placeholder="0.00" required disabled={!active}
                   onChange={(e) => isValidAmountInput(e.target.value) && setAmount(e.target.value)} />
          </label>
          <label className="field">
            <span>Note (optional)</span>
            <input value={note} maxLength={100} disabled={!active} onChange={(e) => setNote(e.target.value)}
                   placeholder={mode === 'DEPOSIT' ? 'e.g. Salary' : 'e.g. ATM'} />
          </label>
          {moneyMessage && <div className={`alert ${moneyMessage.ok ? 'alert-success' : 'alert-error'}`}>{moneyMessage.text}</div>}
          <button className="btn btn-primary btn-block" disabled={!active || moving || !amount}>
            {moving ? 'Processing…' : mode === 'DEPOSIT' ? 'Deposit money' : 'Withdraw money'}
          </button>
          <p className="muted small">Daily limit for withdrawals + transfers: ₹50,000</p>
        </form>

        <div className="card">
          <h3>Monthly statement</h3>
          <p className="muted small">A PDF with your opening balance, every transaction and closing balance.</p>
          <label className="field">
            <span>Month</span>
            <input type="month" value={month} max={currentMonth()} onChange={(e) => setMonth(e.target.value)} required />
          </label>
          {statementError && <div className="alert alert-error">{statementError}</div>}
          <button className="btn btn-ghost btn-block" onClick={downloadStatement} disabled={downloading || !month}>
            {downloading ? 'Preparing PDF…' : '⬇ Download PDF statement'}
          </button>
        </div>
      </div>

      <div className="card">
        <div className="page-header">
          <h3>Transaction history</h3>
          <form className="filter-row" onSubmit={handleFilter}>
            <label>From <input type="date" value={filters.from} onChange={(e) => setFilters({ ...filters, from: e.target.value })} /></label>
            <label>To <input type="date" value={filters.to} onChange={(e) => setFilters({ ...filters, to: e.target.value })} /></label>
            <button className="btn btn-ghost btn-sm">Apply</button>
            {(applied.from || applied.to) && <button type="button" className="btn btn-link btn-sm" onClick={clearFilter}>Clear</button>}
          </form>
        </div>

        {!history && <p className="muted">Loading…</p>}
        {history?.content.length === 0 && <p className="muted center">No transactions for this period.</p>}

        {history?.content.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr><th>Date</th><th>Details</th><th className="num">Debit</th><th className="num">Credit</th><th className="num">Balance</th></tr>
              </thead>
              <tbody>
                {history.content.map((t) => (
                  <tr key={t.id}>
                    <td className="nowrap">{formatDateTime(t.createdAt)}</td>
                    <td>
                      <div>{t.counterparty}</div>
                      <div className="muted small">{t.description ? `${t.description} · ` : ''}Ref {t.reference.slice(0, 8).toUpperCase()}</div>
                    </td>
                    <td className="num debit">{t.direction === 'DEBIT' ? formatMoney(t.amount) : ''}</td>
                    <td className="num credit">{t.direction === 'CREDIT' ? formatMoney(t.amount) : ''}</td>
                    <td className="num nowrap">{formatMoney(t.balanceAfter)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {history?.totalPages > 1 && (
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>← Newer</button>
            <span className="muted">Page {page + 1} of {history.totalPages}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= history.totalPages} onClick={() => setPage(page + 1)}>Older →</button>
          </div>
        )}
      </div>
    </>
  )
}
