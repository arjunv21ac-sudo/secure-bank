import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { beneficiaryApi, getErrorMessage } from '../api/client'

export default function Beneficiaries() {
  const [list, setList] = useState(null)
  const [form, setForm] = useState({ nickname: '', accountNumber: '' })
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [saving, setSaving] = useState(false)

  const load = () => beneficiaryApi.list().then(setList).catch(async (err) => setError(await getErrorMessage(err)))

  useEffect(() => {
    load()
  }, [])

  const handleAdd = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError('')
    setSuccess('')
    try {
      const added = await beneficiaryApi.add(form.nickname, form.accountNumber)
      setSuccess(`Saved ${added.nickname} (${added.holderName}).`)
      setForm({ nickname: '', accountNumber: '' })
      await load()
    } catch (err) {
      setError(await getErrorMessage(err))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async (b) => {
    if (!window.confirm(`Remove ${b.nickname} from your beneficiaries?`)) return
    setError('')
    try {
      await beneficiaryApi.remove(b.id)
      setList((items) => items.filter((x) => x.id !== b.id))
    } catch (err) {
      setError(await getErrorMessage(err))
    }
  }

  return (
    <>
      <div className="page-header"><h1>Beneficiaries</h1></div>
      {error && <div className="alert alert-error" role="alert">{error}</div>}
      {success && <div className="alert alert-success">{success}</div>}

      <div className="two-col">
        <form className="card" onSubmit={handleAdd}>
          <h3>Add a beneficiary</h3>
          <p className="muted small">The bank checks the account exists before saving it.</p>
          <label className="field">
            <span>Nickname</span>
            <input value={form.nickname} maxLength={100} required placeholder="e.g. Kavya (sister)"
                   onChange={(e) => setForm({ ...form, nickname: e.target.value })} />
          </label>
          <label className="field">
            <span>Account number</span>
            <input value={form.accountNumber} maxLength={20} required placeholder="e.g. SB1000000003"
                   onChange={(e) => setForm({ ...form, accountNumber: e.target.value.toUpperCase() })} />
          </label>
          <button className="btn btn-primary btn-block" disabled={saving}>{saving ? 'Verifying…' : 'Save beneficiary'}</button>
        </form>

        <div className="card">
          <h3>Saved</h3>
          {!list && <p className="muted">Loading…</p>}
          {list?.length === 0 && <p className="muted">No beneficiaries yet.</p>}
          <ul className="ben-list">
            {list?.map((b) => (
              <li key={b.id}>
                <div>
                  <strong>{b.nickname}</strong>
                  <div className="muted small">{b.holderName} · {b.accountNumber}</div>
                </div>
                <div className="row-gap">
                  <Link to={`/transfer?to=${b.accountNumber}`} className="btn btn-primary btn-sm">Pay</Link>
                  <button className="btn btn-danger-outline btn-sm" onClick={() => handleDelete(b)}>Remove</button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </>
  )
}
