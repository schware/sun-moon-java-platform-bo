import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import {
  apiGet,
  apiWrite,
  type MasterDefinition,
  type MasterField,
  type MasterRow,
  type Me
} from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 기준정보 — **화면 하나가 기준정보 전부**를 그립니다.
 *
 * 서버가 정의(어떤 칸이 있고, 무슨 종류이고, 무엇이 필수이고, 무엇으로
 * 검색하는가)를 주고 이 화면은 그대로 그립니다. 브랜드를 붙이든 거래처를
 * 붙이든 이 파일은 한 줄도 안 바뀝니다 — 그것이 공통 CRUD의 요점입니다.
 *
 * 대가도 분명합니다: **표가 아닌 것은 여기 못 들어옵니다.** 결품처럼
 * 눌러서 걸고 푸는 화면, 매출처럼 접어서 보는 화면, Live Check처럼 축을
 * 나눠 보는 화면은 격자로 만들면 못 쓰게 됩니다. 그런 화면은 손으로
 * 만듭니다.
 */

type Draft = Record<string, unknown>

export default function MasterPage({ me }: { me: Me }) {
  const { code = '' } = useParams()
  const [definition, setDefinition] = useState<MasterDefinition | null>(null)
  const [rows, setRows] = useState<MasterRow[]>([])
  const [filters, setFilters] = useState<Record<string, string>>({})
  const [draft, setDraft] = useState<Draft | null>(null)
  // 고치는 중인 행의 키. null 이면 새 행입니다.
  const [editingId, setEditingId] = useState<string | null>(null)
  const [history, setHistory] = useState<MasterRow[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const mayView = can(me, 'MASTER', 'VIEW')
  const mayCreate = can(me, 'MASTER', 'CREATE')
  const maySave = can(me, 'MASTER', 'SAVE')
  const mayDelete = can(me, 'MASTER', 'DELETE')

  const load = useCallback(async () => {
    if (!mayView || !code) return
    setLoading(true)
    try {
      const q = new URLSearchParams(
        Object.entries(filters).filter(([, v]) => v !== '') as [string, string][])
      const answer = await apiGet<{ definition: MasterDefinition; rows: MasterRow[] }>(
        `/master/${code}${q.toString() ? `?${q}` : ''}`)
      setDefinition(answer.definition)
      setRows(answer.rows)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '기준정보를 불러오지 못했습니다')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [mayView, code, filters])

  useEffect(() => {
    void load()
  }, [load])

  // 다른 기준정보로 옮기면 쓰던 것을 전부 버립니다 — 브랜드 검색 조건이
  // 거래처 목록에 걸려 있으면 "왜 아무것도 안 나오지"가 됩니다.
  useEffect(() => {
    setFilters({})
    setDraft(null)
    setEditingId(null)
    setHistory(null)
    setNotice(null)
  }, [code])

  const idColumn = definition?.idColumn ?? ''

  function startNew() {
    if (!definition) return
    const blank: Draft = {}
    for (const field of definition.fields) {
      blank[field.name] = field.type === 'BOOLEAN' ? true : ''
    }
    setDraft(blank)
    setEditingId(null)
    setHistory(null)
  }

  function startEdit(row: MasterRow) {
    setDraft({ ...row })
    setEditingId(String(row[idColumn]))
    setHistory(null)
  }

  async function save() {
    if (!definition || draft === null) return
    setBusy(true)
    setError(null)
    try {
      if (editingId === null) {
        await apiWrite('POST', `/master/${definition.code}`, draft)
        setNotice(`${definition.label} 등록했습니다`)
      } else {
        await apiWrite('PUT', `/master/${definition.code}/${encodeURIComponent(editingId)}`, draft)
        setNotice(`${definition.label} 저장했습니다`)
      }
      setDraft(null)
      setEditingId(null)
      await load()
    } catch (e) {
      // 서버가 만든 문장을 그대로 보여줍니다 — "회사명은(는) 필수입니다"
      // 처럼 운영자가 읽고 바로 고칠 수 있는 말이라서요.
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(row: MasterRow) {
    if (!definition) return
    const id = String(row[idColumn])
    if (!window.confirm(`${id} 을(를) 지울까요? 지운 값은 변경 이력에 남습니다.`)) return
    setBusy(true)
    setError(null)
    try {
      await apiWrite('DELETE', `/master/${definition.code}/${encodeURIComponent(id)}`)
      setNotice(`${id} 지웠습니다`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '지우지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function showHistory(row: MasterRow) {
    if (!definition) return
    const id = String(row[idColumn])
    setDraft(null)
    try {
      setHistory(await apiGet<MasterRow[]>(
        `/master/${definition.code}/${encodeURIComponent(id)}/history`))
    } catch (e) {
      setError(e instanceof Error ? e.message : '변경 이력을 불러오지 못했습니다')
    }
  }

  const searchable = useMemo(
    () => definition?.fields.filter((f) => f.searchable) ?? [], [definition])

  if (!mayView) return <div className="empty">이 화면에 대한 권한이 없습니다.</div>
  if (!definition && loading) return <div className="empty">불러오는 중…</div>
  if (!definition) {
    return (
      <>
        {error && <div className="error" role="alert">{error}</div>}
        <div className="empty">그런 기준정보가 없습니다: {code}</div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>{definition.label}</h1>
        <div className="row">
          <button className="btn" onClick={() => void load()}>새로고침</button>
          {mayCreate && <button className="btn primary" onClick={startNew}>새로 만들기</button>}
        </div>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: 18 }}>
        이 화면은 <strong>서버가 준 정의</strong>대로 그려집니다 — 열도, 입력칸도, 검색
        조건도 코드에 없습니다. 기준정보를 하나 더 붙여도 이 화면은 바뀌지 않습니다.
        고치고 지운 것은 전부 변경 이력에 남습니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      {searchable.length > 0 && (
        <div className="panel">
          <div className="row">
            {searchable.map((field) => (
              <label key={field.name} className="field">
                <span className="muted">{field.label}</span>
                <input
                  value={filters[field.name] ?? ''}
                  onChange={(e) => setFilters((f) => ({ ...f, [field.name]: e.target.value }))}
                  placeholder={field.type === 'BOOLEAN' ? 'true / false' : ''}
                />
              </label>
            ))}
            {/* 글자 검색은 앞부분 일치입니다. 가운데 일치로 두면 색인을
                못 쓰고, 느려지는 이유가 화면에 안 보입니다. */}
            <span className="muted" style={{ fontSize: 12 }}>앞부분이 일치하면 찾습니다</span>
          </div>
        </div>
      )}

      {draft !== null && (
        <div className="panel" style={{ marginTop: 16 }}>
          <h2 style={{ fontSize: 15, margin: '0 0 14px' }}>
            {editingId === null ? '새 ' + definition.label : `${editingId} 고치기`}
          </h2>
          <div className="master-form">
            {definition.fields.map((field) => (
              <FieldInput
                key={field.name}
                field={field}
                // 키는 새로 만들 때만 칠 수 있습니다. 키를 바꾸는 것은
                // 수정이 아니라 다른 행을 만드는 일이라서요.
                disabled={editingId !== null && !field.editable}
                value={draft[field.name]}
                onChange={(value) => setDraft((d) => ({ ...(d ?? {}), [field.name]: value }))}
              />
            ))}
          </div>
          <div className="row" style={{ marginTop: 14 }}>
            <button
              className="btn primary"
              disabled={busy || (editingId === null ? !mayCreate : !maySave)}
              onClick={() => void save()}
            >
              {busy ? '저장 중…' : '저장'}
            </button>
            <button className="btn" onClick={() => { setDraft(null); setEditingId(null) }}>
              취소
            </button>
          </div>
        </div>
      )}

      <div className="panel" style={{ marginTop: 16 }}>
        {loading && rows.length === 0 ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">아직 등록된 {definition.label}이(가) 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {definition.fields.map((field) => <th key={field.name}>{field.label}</th>)}
                  <th className="actions"></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={String(row[idColumn])}>
                    {definition.fields.map((field) => (
                      <td key={field.name} className={field.type === 'NUMBER' ? 'num' : undefined}>
                        <Cell field={field} value={row[field.name]} />
                      </td>
                    ))}
                    <td className="actions">
                      <button className="btn link" onClick={() => void showHistory(row)}>이력</button>
                      {maySave && (
                        <button className="btn link" onClick={() => startEdit(row)}>고치기</button>
                      )}
                      {mayDelete && (
                        <button className="btn link danger" disabled={busy}
                                onClick={() => void remove(row)}>지우기</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {history !== null && (
        <div className="panel" style={{ marginTop: 16 }}>
          <div className="page-head" style={{ marginBottom: 8 }}>
            <h2 style={{ fontSize: 15, margin: 0 }}>변경 이력</h2>
            <button className="btn" onClick={() => setHistory(null)}>닫기</button>
          </div>
          <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
            바꾸기 <strong>전</strong> 값도 함께 남습니다 — 바뀐 값만 있으면 무엇이 어떻게
            달라졌는지 알 수 없고, 되돌릴 근거도 없습니다.
          </p>
          {history.length === 0 ? (
            <div className="empty">아직 바뀐 적이 없습니다.</div>
          ) : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr><th>시각</th><th>행위</th><th>바꾼 사람</th><th>이전</th><th>이후</th></tr>
                </thead>
                <tbody>
                  {history.map((entry) => (
                    <tr key={String(entry.id)}>
                      <td className="muted">
                        {new Date(String(entry.changed_at)).toLocaleString('ko-KR')}
                      </td>
                      <td>{String(entry.action)}</td>
                      <td className="muted">{String(entry.actor ?? '')}</td>
                      <td className="muted json">{String(entry.before_value ?? '')}</td>
                      <td className="muted json">{String(entry.after_value ?? '')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </>
  )
}

/** 정의가 말하는 종류대로 입력칸을 고릅니다. */
function FieldInput({ field, value, disabled, onChange }:
  { field: MasterField; value: unknown; disabled: boolean; onChange: (value: unknown) => void }) {
  const label = (
    <span className="muted">
      {field.label}
      {field.required && <span className="req"> *</span>}
    </span>
  )

  if (field.type === 'BOOLEAN') {
    return (
      <label className="field row" style={{ gap: 8 }}>
        <input
          type="checkbox"
          disabled={disabled}
          checked={value === true || value === 'true'}
          onChange={(e) => onChange(e.target.checked)}
        />
        {label}
      </label>
    )
  }

  if (field.type === 'LONG_TEXT') {
    return (
      <label className="field wide">
        {label}
        <textarea
          rows={3}
          disabled={disabled}
          value={String(value ?? '')}
          onChange={(e) => onChange(e.target.value)}
        />
      </label>
    )
  }

  return (
    <label className="field">
      {label}
      <input
        type={field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'}
        // 숫자 칸은 소수도 받습니다 — 수수료율이 2.3 입니다.
        step={field.type === 'NUMBER' ? 'any' : undefined}
        disabled={disabled}
        value={String(value ?? '')}
        onChange={(e) => onChange(e.target.value)}
      />
    </label>
  )
}

/** 표 한 칸. 종류마다 읽는 법이 다릅니다 — true 를 "true"로 쓰면 안 읽힙니다. */
function Cell({ field, value }: { field: MasterField; value: unknown }) {
  if (value === null || value === undefined || value === '') {
    return <span className="muted">—</span>
  }
  if (field.type === 'BOOLEAN') {
    return value === true
      ? <span className="badge on">사용</span>
      : <span className="badge off">미사용</span>
  }
  if (field.type === 'NUMBER') {
    return <>{new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 3 }).format(Number(value))}</>
  }
  return <>{String(value)}</>
}
