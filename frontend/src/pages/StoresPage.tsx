import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiGet, apiWrite, type Me, type Store } from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit' }

const BLANK: Store = { storeId: '', name: '', phone: '', address: '', active: true }

export default function StoresPage({ me }: { me: Me }) {
  const [rows, setRows] = useState<Store[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<Store>(BLANK)
  const [busy, setBusy] = useState(false)

  const mayView = can(me, 'STORE', 'VIEW')
  const mayCreate = can(me, 'STORE', 'CREATE')
  const maySave = can(me, 'STORE', 'SAVE')
  const mayDelete = can(me, 'STORE', 'DELETE')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setRows(await apiGet<Store[]>('/stores'))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [mayView])

  useEffect(() => {
    void load()
  }, [load])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiWrite(editing.mode === 'new' ? 'POST' : 'PUT', '/stores', form)
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(row: Store) {
    if (!window.confirm(`${row.storeId} 을(를) 삭제할까요?`)) return
    setError(null)
    try {
      const q = new URLSearchParams({ storeId: row.storeId })
      await apiWrite('DELETE', `/stores?${q}`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>매장</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>매장</h1>
        <button className="btn primary" disabled={!mayCreate}
                onClick={() => { setForm(BLANK); setEditing({ mode: 'new' }) }}>
          신규
        </button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        기준 정보만 관리합니다 — 개점/마감과 영업일자는 POS에서 처리하고 Order가 갖고 있습니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '신규 매장' : `${form.storeId} 수정`}</h2>
          <div className="field">
            <label htmlFor="storeId">매장 ID</label>
            <input id="storeId" type="text" value={form.storeId} required
                   disabled={editing.mode === 'edit'}
                   onChange={(e) => setForm({ ...form, storeId: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="name">매장명</label>
            <input id="name" type="text" value={form.name} required
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="phone">전화번호</label>
            <input id="phone" type="text" value={form.phone ?? ''}
                   onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="address">주소</label>
            <input id="address" type="text" value={form.address ?? ''}
                   onChange={(e) => setForm({ ...form, address: e.target.value })} />
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
          <div className="empty">등록된 매장이 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>매장 ID</th><th>매장명</th><th>전화번호</th><th>주소</th><th>사용</th><th /></tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.storeId}>
                    <td className="muted">{row.storeId}</td>
                    <td>{row.name}</td>
                    <td className="muted">{row.phone ?? ''}</td>
                    <td className="muted">{row.address ?? ''}</td>
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
