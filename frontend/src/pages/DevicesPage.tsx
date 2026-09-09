import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiGet, apiWrite, type Device, type Me } from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit' }

const BLANK: Device = { deviceId: '', name: '', deviceType: '', location: '', active: true }

export default function DevicesPage({ me }: { me: Me }) {
  const [rows, setRows] = useState<Device[]>([])
  const [types, setTypes] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<Device>(BLANK)
  const [busy, setBusy] = useState(false)

  const mayView = can(me, 'DEVICE', 'VIEW')
  const mayCreate = can(me, 'DEVICE', 'CREATE')
  const maySave = can(me, 'DEVICE', 'SAVE')
  const mayDelete = can(me, 'DEVICE', 'DELETE')
  const mayReadCodes = can(me, 'COMMON_CODE', 'VIEW')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setRows(await apiGet<Device[]>('/devices'))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [mayView])

  // Device type is expected to name a DEVICE_TYPE common code, but is not
  // a foreign key — so this is a convenience list, not a constraint, and
  // an operator without common-code access simply types the value.
  const loadTypes = useCallback(async () => {
    if (!mayReadCodes) return
    try {
      const codes = await apiGet<{ code: string }[]>('/common-code?group=DEVICE_TYPE')
      setTypes(codes.map((c) => c.code))
    } catch {
      setTypes([])
    }
  }, [mayReadCodes])

  useEffect(() => {
    void load()
    void loadTypes()
  }, [load, loadTypes])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiWrite(editing.mode === 'new' ? 'POST' : 'PUT', '/devices', form)
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(row: Device) {
    if (!window.confirm(`${row.deviceId} 을(를) 삭제할까요?`)) return
    setError(null)
    try {
      const q = new URLSearchParams({ deviceId: row.deviceId })
      await apiWrite('DELETE', `/devices?${q}`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>장비</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>장비</h1>
        <button className="btn primary" disabled={!mayCreate}
                onClick={() => { setForm(BLANK); setEditing({ mode: 'new' }) }}>
          신규
        </button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        기준 정보만 관리합니다 — 접속 상태와 handshake는 별도의 Device Server 몫입니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '신규 장비' : `${form.deviceId} 수정`}</h2>
          <div className="field">
            <label htmlFor="deviceId">장비 ID</label>
            <input id="deviceId" type="text" value={form.deviceId} required
                   disabled={editing.mode === 'edit'}
                   onChange={(e) => setForm({ ...form, deviceId: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="name">이름</label>
            <input id="name" type="text" value={form.name} required
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="deviceType">장비 유형</label>
            <input id="deviceType" type="text" value={form.deviceType} required list="device-types"
                   onChange={(e) => setForm({ ...form, deviceType: e.target.value })} />
            <datalist id="device-types">
              {types.map((t) => <option key={t} value={t} />)}
            </datalist>
          </div>
          <div className="field">
            <label htmlFor="location">위치</label>
            <input id="location" type="text" value={form.location ?? ''}
                   onChange={(e) => setForm({ ...form, location: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="active">사용여부</label>
            <select id="active" value={form.active ? 'Y' : 'N'}
                    onChange={(e) => setForm({ ...form, active: e.target.value === 'Y' })}>
              <option value="Y">사용</option>
              <option value="N">미사용</option>
            </select>
          </div>
          <div className="row">
            <button className="btn primary" type="submit" disabled={busy}>{busy ? '저장 중…' : '저장'}</button>
            <button className="btn" type="button" onClick={() => setEditing({ mode: 'none' })}>취소</button>
          </div>
        </form>
      )}

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">등록된 장비가 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>장비 ID</th><th>이름</th><th>유형</th><th>위치</th><th>사용</th><th /></tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.deviceId}>
                    <td className="muted">{row.deviceId}</td>
                    <td>{row.name}</td>
                    <td>{row.deviceType}</td>
                    <td className="muted">{row.location ?? ''}</td>
                    <td>{row.active ? <span className="badge on">사용</span> : <span className="badge off">미사용</span>}</td>
                    <td className="actions">
                      <div className="row" style={{ justifyContent: 'flex-end' }}>
                        <button className="btn" disabled={!maySave}
                                onClick={() => { setForm(row); setEditing({ mode: 'edit' }) }}>수정</button>
                        <button className="btn danger" disabled={!mayDelete}
                                onClick={() => void remove(row)}>삭제</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
