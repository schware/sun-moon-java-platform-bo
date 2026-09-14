import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  apiGet, apiWrite,
  type Me, type Operator, type OperatorRequest, type ScreenPermission
} from '../lib/api'
import { ACTIONS, SCREENS, SCREEN_LABEL, VIEW_ONLY_SCREENS, can, type Action, type Screen } from '../lib/permissions'

/**
 * 운영자 관리 — the screen that finally makes the three permission tiers
 * reachable. They have been enforced since BO's first commit; until now
 * the only way to grant one was an INSERT by hand.
 *
 * The permission matrix posts every screen, ticked or not, and the server
 * replaces the whole set. Merging would mean an unticked box silently
 * keeping the permission it was meant to remove.
 */

const ACTION_LABEL: Record<Action, string> = {
  VIEW: '조회', CREATE: '신규', SAVE: '저장', DELETE: '삭제'
}

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit'; id: number }

function blankMatrix(): ScreenPermission[] {
  return SCREENS.map((screen) => ({
    screen, canView: false, canCreate: false, canSave: false, canDelete: false
  }))
}

function matrixOf(operator: Operator): ScreenPermission[] {
  return SCREENS.map((screen) =>
    operator.permissions.find((p) => p.screen === screen)
      ?? { screen, canView: false, canCreate: false, canSave: false, canDelete: false })
}

const BLANK: OperatorRequest = {
  username: '', password: '', displayName: '', superAdmin: false, active: true,
  permissions: blankMatrix()
}

const FIELD_OF: Record<Action, keyof Omit<ScreenPermission, 'screen'>> = {
  VIEW: 'canView', CREATE: 'canCreate', SAVE: 'canSave', DELETE: 'canDelete'
}

export default function OperatorsPage({ me }: { me: Me }) {
  const [rows, setRows] = useState<Operator[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<OperatorRequest>(BLANK)
  const [busy, setBusy] = useState(false)

  const mayView = can(me, 'OPERATOR', 'VIEW')
  const mayCreate = can(me, 'OPERATOR', 'CREATE')
  const maySave = can(me, 'OPERATOR', 'SAVE')
  const mayDelete = can(me, 'OPERATOR', 'DELETE')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setRows(await apiGet<Operator[]>('/operators'))
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

  function startNew() {
    setForm({ ...BLANK, permissions: blankMatrix() })
    setEditing({ mode: 'new' })
  }

  function startEdit(operator: Operator) {
    setForm({
      username: operator.username,
      // Blank means "leave it": editing someone's permissions should not
      // require resetting their password.
      password: '',
      displayName: operator.displayName,
      superAdmin: operator.superAdmin,
      active: operator.active,
      permissions: matrixOf(operator)
    })
    setEditing({ mode: 'edit', id: operator.id })
  }

  function togglePermission(screen: Screen, action: Action) {
    const field = FIELD_OF[action]
    setForm((prev) => ({
      ...prev,
      permissions: prev.permissions.map((p) =>
        p.screen === screen ? { ...p, [field]: !p[field] } : p)
    }))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (editing.mode === 'new') {
        await apiWrite('POST', '/operators', form)
      } else if (editing.mode === 'edit') {
        await apiWrite('PUT', `/operators/${editing.id}`, form)
      }
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(operator: Operator) {
    if (!window.confirm(`${operator.username} 계정을 삭제할까요?`)) return
    setError(null)
    try {
      await apiWrite('DELETE', `/operators/${operator.id}`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>운영자</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  const isSuper = form.superAdmin

  return (
    <>
      <div className="page-head">
        <h1>운영자</h1>
        <button className="btn primary" disabled={!mayCreate} onClick={startNew}>신규</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        전체 관리자는 모든 화면을 통과하므로 아래 권한 표를 보지 않습니다. 마지막 전체 관리자는
        권한을 내리거나 사용 중지하거나 삭제할 수 없습니다 — 그렇게 하면 아무도 이 화면에
        들어올 수 없게 되고, 초기 계정은 운영자 테이블이 <em>비어 있을 때만</em> 만들어집니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '신규 운영자' : `${form.username} 수정`}</h2>

          <div className="field">
            <label htmlFor="username">아이디</label>
            <input id="username" type="text" value={form.username} required
                   disabled={editing.mode === 'edit'}
                   onChange={(e) => setForm({ ...form, username: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="displayName">이름</label>
            <input id="displayName" type="text" value={form.displayName} required
                   onChange={(e) => setForm({ ...form, displayName: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="password">
              비밀번호 {editing.mode === 'edit' && <span className="muted">(비워 두면 그대로)</span>}
            </label>
            <input id="password" type="password" autoComplete="new-password"
                   value={form.password} required={editing.mode === 'new'} minLength={8}
                   onChange={(e) => setForm({ ...form, password: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="superAdmin">권한 등급</label>
            <select id="superAdmin" value={form.superAdmin ? 'Y' : 'N'}
                    onChange={(e) => setForm({ ...form, superAdmin: e.target.value === 'Y' })}>
              <option value="N">일반 운영자 (화면별 권한)</option>
              <option value="Y">전체 관리자</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="active">사용여부</label>
            <select id="active" value={form.active ? 'Y' : 'N'}
                    onChange={(e) => setForm({ ...form, active: e.target.value === 'Y' })}>
              <option value="Y">사용</option>
              <option value="N">미사용</option>
            </select>
          </div>

          <div className="field">
            <label>화면별 권한</label>
            {isSuper ? (
              <p className="muted">전체 관리자는 모든 화면과 행위를 통과합니다.</p>
            ) : (
              <div className="table-wrap">
                <table className="matrix">
                  <thead>
                    <tr>
                      <th>화면</th>
                      {ACTIONS.map((action) => <th key={action}>{ACTION_LABEL[action]}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {form.permissions.map((permission) => {
                      const screen = permission.screen as Screen
                      const viewOnly = VIEW_ONLY_SCREENS.includes(screen)
                      return (
                        <tr key={screen}>
                          <td>{SCREEN_LABEL[screen] ?? screen}</td>
                          {ACTIONS.map((action) => {
                            // 매출 조회와 단말 현황에는 쓰기 엔드포인트가
                            // 아예 없습니다 — 체크할 수 있게 두면 아무 일도
                            // 하지 않는 권한을 주게 됩니다.
                            const dead = viewOnly && action !== 'VIEW'
                            return (
                              <td key={action}>
                                <input type="checkbox" disabled={dead}
                                       checked={!dead && permission[FIELD_OF[action]]}
                                       onChange={() => togglePermission(screen, action)} />
                              </td>
                            )
                          })}
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          <div className="row">
            <button className="btn primary" type="submit" disabled={busy}>
              {busy ? '저장 중…' : '저장'}
            </button>
            <button className="btn" type="button" onClick={() => setEditing({ mode: 'none' })}>취소</button>
          </div>
        </form>
      )}

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">등록된 운영자가 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>아이디</th><th>이름</th><th>등급</th><th>권한</th><th>사용</th><th /></tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      {row.username}
                      {row.username === me.username && <span className="badge" style={{ marginLeft: 6 }}>본인</span>}
                    </td>
                    <td>{row.displayName}</td>
                    <td>
                      {row.superAdmin
                        ? <span className="badge">전체 관리자</span>
                        : <span className="muted">일반</span>}
                    </td>
                    <td className="muted">
                      {row.superAdmin
                        ? '모든 화면'
                        : row.permissions.length === 0
                          ? '없음'
                          : row.permissions
                              .map((p) => SCREEN_LABEL[p.screen as Screen] ?? p.screen)
                              .join(', ')}
                    </td>
                    <td>
                      {row.active
                        ? <span className="badge on">사용</span>
                        : <span className="badge off">미사용</span>}
                    </td>
                    <td className="actions">
                      <div className="row" style={{ justifyContent: 'flex-end' }}>
                        <button className="btn" disabled={!maySave}
                                onClick={() => startEdit(row)}>수정</button>
                        <button className="btn danger"
                                disabled={!mayDelete || row.username === me.username}
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
