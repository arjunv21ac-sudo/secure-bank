import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { accountApi, beneficiaryApi, getErrorMessage, transferApi } from '../api/client'
import { ACCOUNT_TYPE_LABEL, formatDateTime, formatMoney, isValidAmountInput } from '../utils/format'

const EMPTY = { fromAccountId: '', toAccountNumber: '', amount: '', remarks: '' }

/**
 * 3 steps: FORM -> REVIEW (receiver's name confirmed by the bank) -> DONE (receipt).
 * One Idempotency-Key is created per transfer. If "Confirm" is clicked twice or the network fails
 * and the user retries, the same key is sent again, so the bank sends the money only once.
 */
export default function Transfer() {
  const [searchParams] = useSearchParams()

  const [accounts, setAccounts] = useState(null)
  const [beneficiaries, setBeneficiaries] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [step, setStep] = useState('FORM')
  const [receiver, setReceiver] = useState(null) // { accountNumber, holderName }
  const [receipt, setReceipt] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const idempotencyKey = useRef(crypto.randomUUID())
  // A ref updates instantly; `busy` state only after re-render, so two fast clicks would both pass it.
  // (Even if both requests went out, the shared Idempotency-Key makes the bank pay once.)
  const sending = useRef(false)

  useEffect(() => {
    Promise.all([accountApi.mine(), beneficiaryApi.list()])
      .then(([accs, bens]) => {
        const active = accs.filter((a) => a.status === 'ACTIVE')
        setAccounts(active)
        setBeneficiaries(bens)
        const preferredFrom = active.find((a) => String(a.id) === searchParams.get('from'))
        setForm((f) => ({
          ...f,
          fromAccountId: String((preferredFrom || active[0])?.id ?? ''),
          toAccountNumber: searchParams.get('to') || '',
        }))
      })
      .catch(async (err) => setError(await getErrorMessage(err)))
  }, [searchParams])

  const update = (field, value) => setForm((f) => ({ ...f, [field]: value }))
  const fromAccount = accounts?.find((a) => String(a.id) === form.fromAccountId)

  // Step 1 -> 2: ask the bank whose account this is before any money moves
  const handleReview = async (e) => {
    e.preventDefault()
    setError('')
    if (Number(form.amount) <= 0) {
      setError('Enter an amount greater than zero')
      return
    }
    if (fromAccount && Number(form.amount) > Number(fromAccount.balance)) {
      setError(`Insufficient balance. Available: ${formatMoney(fromAccount.balance)}`)
      return
    }
    setBusy(true)
    try {
      setReceiver(await accountApi.lookup(form.toAccountNumber))
      setStep('REVIEW')
    } catch (err) {
      setError(await getErrorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  // Step 2 -> 3: send. Retrying reuses the same key, so it is always safe.
  const handleConfirm = async () => {
    if (sending.current) return
    sending.current = true
    setBusy(true)
    setError('')
    try {
      const txn = await transferApi.send({
        fromAccountId: Number(form.fromAccountId),
        toAccountNumber: receiver.accountNumber,
        amount: form.amount,
        remarks: form.remarks || null,
      }, idempotencyKey.current)
      setReceipt(txn)
      setStep('DONE')
    } catch (err) {
      const message = await getErrorMessage(err)
      setError(err.response ? message : `${message} Retrying is safe: you will not be charged twice.`)
    } finally {
      sending.current = false
      setBusy(false)
    }
  }

  const startNew = () => {
    idempotencyKey.current = crypto.randomUUID()
    setForm({ ...EMPTY, fromAccountId: form.fromAccountId })
    setReceiver(null)
    setReceipt(null)
    setError('')
    setStep('FORM')
  }

  if (!accounts && !error) return <p className="center muted">Loading…</p>

  if (accounts?.length === 0) {
    return (
      <div className="empty">
        <p>You need an active account to send money.</p>
        <Link to="/dashboard" className="btn btn-primary">Go to accounts</Link>
      </div>
    )
  }

  return (
    <div className="narrow">
      <div className="steps" aria-label="Transfer progress">
        {['Details', 'Review', 'Done'].map((label, i) => (
          <span key={label} className={`step ${['FORM', 'REVIEW', 'DONE'].indexOf(step) >= i ? 'on' : ''}`}>{i + 1}. {label}</span>
        ))}
      </div>

      {error && <div className="alert alert-error" role="alert">{error}</div>}

      {step === 'FORM' && (
        <form className="card" onSubmit={handleReview}>
          <h1>Send money</h1>
          <label className="field">
            <span>From</span>
            <select value={form.fromAccountId} onChange={(e) => update('fromAccountId', e.target.value)} required>
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {ACCOUNT_TYPE_LABEL[a.type]} · {a.accountNumber} · {formatMoney(a.balance)}
                </option>
              ))}
            </select>
          </label>

          {beneficiaries.length > 0 && (
            <label className="field">
              <span>Pay a saved beneficiary</span>
              <select value="" onChange={(e) => update('toAccountNumber', e.target.value)}>
                <option value="">Choose…</option>
                {beneficiaries.map((b) => (
                  <option key={b.id} value={b.accountNumber}>{b.nickname} · {b.accountNumber}</option>
                ))}
              </select>
            </label>
          )}

          <label className="field">
            <span>To account number</span>
            <input value={form.toAccountNumber} onChange={(e) => update('toAccountNumber', e.target.value.toUpperCase())}
                   placeholder="e.g. SB1000000003" required maxLength={20} />
          </label>
          <label className="field">
            <span>Amount (₹)</span>
            <input inputMode="decimal" value={form.amount} placeholder="0.00" required
                   onChange={(e) => isValidAmountInput(e.target.value) && update('amount', e.target.value)} />
          </label>
          <label className="field">
            <span>Remarks (optional)</span>
            <input value={form.remarks} maxLength={100} onChange={(e) => update('remarks', e.target.value)} placeholder="e.g. Rent share" />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Checking account…' : 'Continue'}</button>
        </form>
      )}

      {step === 'REVIEW' && (
        <div className="card">
          <h1>Review transfer</h1>
          <dl className="review">
            <dt>From</dt><dd>{fromAccount.accountNumber} ({ACCOUNT_TYPE_LABEL[fromAccount.type]})</dd>
            <dt>To</dt><dd><strong>{receiver.holderName}</strong><br /><span className="muted">{receiver.accountNumber}</span></dd>
            <dt>Amount</dt><dd className="review-amount">{formatMoney(form.amount)}</dd>
            {form.remarks && <><dt>Remarks</dt><dd>{form.remarks}</dd></>}
          </dl>
          <p className="muted small">Check the name. Money sent to a wrong account cannot be pulled back automatically.</p>
          <div className="row-gap">
            <button className="btn btn-ghost" onClick={() => setStep('FORM')} disabled={busy}>← Edit</button>
            <button className="btn btn-primary" onClick={handleConfirm} disabled={busy}>
              {busy ? 'Sending…' : `Confirm & send ${formatMoney(form.amount)}`}
            </button>
          </div>
        </div>
      )}

      {step === 'DONE' && receipt && (
        <div className="card receipt">
          <div className="receipt-icon">✅</div>
          <h1>Transfer successful</h1>
          <p className="review-amount">{formatMoney(receipt.amount)}</p>
          <dl className="review">
            <dt>To</dt><dd>{receipt.counterparty}</dd>
            <dt>Reference</dt><dd><code>{receipt.reference}</code></dd>
            <dt>Date</dt><dd>{formatDateTime(receipt.createdAt)}</dd>
            <dt>Balance after</dt><dd>{formatMoney(receipt.balanceAfter)}</dd>
          </dl>
          <div className="row-gap">
            <button className="btn btn-primary" onClick={startNew}>Make another transfer</button>
            <Link to={`/accounts/${form.fromAccountId}`} className="btn btn-ghost">View account</Link>
          </div>
        </div>
      )}
    </div>
  )
}
