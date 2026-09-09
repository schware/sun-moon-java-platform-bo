import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiGet, apiWrite, type CommonCode, type Me } from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit'; original: CommonCode }

const BLANK: CommonCode = { groupCode: '', code: '', name: '', sortOrder: 0, active: true }

export default function CommonCodePage({ me }: { me: Me }) {
  const [rows, setRows] = useState<CommonCode[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<CommonCode>(BLANK)
  const [busy, setBusy] = useState(false)

  const mayView = can(me, 'COMMON_CODE', 'VIEW')
  const mayCreate = can(me, 'COMMON_CODE', 'CREATE')
  const maySave = can(me, 'COMMON_CODE', 'SAVE')
  const mayDelete = can(me, 'COMMON_CODE', 'DELETE')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setRows(await apiGet<CommonCode[]>('/common-code'))
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
      await apiWrite(editing.mode === 'new' ? 'POST' : 'PUT', '/common-code', form)
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(row: CommonCode) {
    if (!window.confirm(`${row.groupCode} / ${row.code} 을(를) 삭제할까요?`)) return
    setError(null)
    try {
      // This screen keys on (groupCode, code) in the query string — a
      // carry-over from the Netty router that had no path variables.
      const q = new URLSearchParams({ group: row.groupCode, code: row.code })
      await apiWrite('DELETE', `/common-code?${q}`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>공통코드</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>공통코드</h1>
        <button
          className="btn primary"
          disabled={!mayCreate}
          onClick={() => { setForm(BLANK); setEditing({ mode: 'new' }) }}
        >
          신규
        </button>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '신규 코드' : `${form.groupCode} / ${form.code} 수정`}</h2>
          <div className="field">
            <label htmlFor="groupCode">그룹코드</label>
            <input id="groupCode" type="text" value={form.groupCode} required
                   disabled={editing.mode === 'edit'}
                   onChange={(e) => setForm({ ...form, groupCode: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="code">코드</label>
            <input id="code" type="text" value={form.code} required
                   disabled={editing.mode === 'edit'}
                   onChange={(e) => setForm({ ...form, code: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="name">이름</label>
            <input id="name" type="text" value={form.name} required
                   onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="sortOrder">정렬순서</label>
            <input id="sortOrder" type="number" min={0} value={form.sortOrder}
                   onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) })} />
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
          <div className="empty">등록된 공통코드가 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>그룹코드</th><th>코드</th><th>이름</th><th>정렬</th><th>사용</th><th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={`${row.groupCode}/${row.code}`}>
                    <td className="muted">{row.groupCode}</td>
                    <td>{row.code}</td>
                    <td>{row.name}</td>
                    <td className="muted">{row.sortOrder}</td>
                    <td>{row.active ? <span className="badge on">사용</span> : <span className="badge off">미사용</span>}</td>
                    <td className="actions">
                      <div className="row" style={{ justifyContent: 'flex-end' }}>
                        <button className="btn" disabled={!maySave}
                                onClick={() => { setForm(row); setEditing({ mode: 'edit', original: row }) }}>수정</button>
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
