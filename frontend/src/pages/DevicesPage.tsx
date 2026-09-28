import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  apiGet, apiWrite,
  type ConnectionCheckResult, type Device, type DevicePairingRow, type Me, type Store, type TerminalType
} from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit' }
type CheckState = 'checking' | ConnectionCheckResult

const BLANK: Device = {
  deviceId: '', name: '', deviceType: '', location: '', storeId: null,
  terminalType: null, ipAddress: '', port: null, active: true, kdsRole: null
}

/** 발급된 코드는 이 화면에서 한 번만 보입니다 — Device Server 도 코드를 다시 알려주지 않습니다. */
type IssuedCode = { deviceId: string; code: string; expiresAt: string }

function pairingKey(storeId: string | null, deviceId: string, type: string | null) {
  return `${storeId ?? ''}|${deviceId}|${type ?? ''}`
}

function hhmm(iso: string | null) {
  return iso ? new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) : ''
}

const CHECK_LABEL: Record<CheckState, string> = {
  checking: '확인 중…',
  REACHABLE: '응답함',
  UNREACHABLE: '응답 없음',
  NO_ADDRESS: 'IP/PORT 없음'
}

export default function DevicesPage({ me }: { me: Me }) {
  const [rows, setRows] = useState<Device[]>([])
  const [types, setTypes] = useState<string[]>([])
  const [stores, setStores] = useState<Store[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [form, setForm] = useState<Device>(BLANK)
  const [busy, setBusy] = useState(false)
  const [checks, setChecks] = useState<Record<string, CheckState>>({})
  const [pairing, setPairing] = useState<Record<string, DevicePairingRow>>({})
  const [pairingError, setPairingError] = useState<string | null>(null)
  const [issued, setIssued] = useState<IssuedCode | null>(null)

  const mayView = can(me, 'DEVICE', 'VIEW')
  const mayCreate = can(me, 'DEVICE', 'CREATE')
  const maySave = can(me, 'DEVICE', 'SAVE')
  const mayDelete = can(me, 'DEVICE', 'DELETE')
  const mayReadCodes = can(me, 'COMMON_CODE', 'VIEW')
  const mayReadStores = can(me, 'STORE', 'VIEW')

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

  // 페어링 상태는 Device Server 의 것이라 따로 읽습니다. 못 읽어도 장비 목록은 그대로 보입니다 —
  // 기준 정보 관리가 Device Server 에 매이지 않게.
  const loadPairing = useCallback(async () => {
    if (!mayView) return
    try {
      const list = await apiGet<DevicePairingRow[]>('/devices/pairing')
      const byKey: Record<string, DevicePairingRow> = {}
      for (const p of list) {
        const key = pairingKey(p.storeId, p.deviceId, p.deviceType)
        // 페어링된 것이 코드 발급보다 앞섭니다 — 둘 다 있으면 다시 페어링하는 중이지만 지금은 페어링된 상태.
        if (!byKey[key] || p.state === 'PAIRED') byKey[key] = p
      }
      setPairing(byKey)
      setPairingError(null)
    } catch (e) {
      setPairingError(e instanceof Error ? e.message : 'POS-SERVER 에서 페어링 상태를 읽지 못했습니다')
    }
  }, [mayView])

  useEffect(() => {
    void load()
    void loadTypes()
    void loadPairing()
  }, [load, loadTypes, loadPairing])

  async function issueCode(row: Device) {
    setError(null)
    try {
      const res = await apiWrite<{ code: string; expiresAt: string }>(
        'POST', `/devices/${encodeURIComponent(row.deviceId)}/pairing-code`
      )
      if (res) setIssued({ deviceId: row.deviceId, code: res.code, expiresAt: res.expiresAt })
      await loadPairing()
    } catch (e) {
      setError(e instanceof Error ? e.message : '페어링 코드를 받지 못했습니다')
    }
  }

  async function unpair(row: Device) {
    if (!window.confirm(`${row.deviceId} 의 페어링을 해제할까요? 그 기기는 코드를 다시 넣어야 합니다.`)) return
    setError(null)
    try {
      await apiWrite('POST', `/devices/${encodeURIComponent(row.deviceId)}/unpair`)
      await loadPairing()
    } catch (e) {
      setError(e instanceof Error ? e.message : '해제하지 못했습니다')
    }
  }

  // A terminal belongs to a 매장, and the Device Server identifies one by
  // (매장, 단말기번호, 종류) — every branch has a pos-01. Picked from the
  // 매장 master rather than typed, so 단말 현황 can actually match the two.
  useEffect(() => {
    if (!mayReadStores) return
    apiGet<Store[]>('/stores').then(setStores).catch(() => setStores([]))
  }, [mayReadStores])

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await apiWrite(editing.mode === 'new' ? 'POST' : 'PUT', '/devices', {
        ...form,
        port: form.port === null || Number.isNaN(form.port) ? null : form.port
      })
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

  // On demand, one device at a time — there is deliberately no polling
  // here. Whether a terminal is actually connected is the Device Server's
  // /terminals; this just asks whether the recorded address answers.
  async function checkConnection(row: Device) {
    setChecks((prev) => ({ ...prev, [row.deviceId]: 'checking' }))
    try {
      const res = await apiWrite<{ result: ConnectionCheckResult }>(
        'POST', `/devices/${encodeURIComponent(row.deviceId)}/connection-check`
      )
      setChecks((prev) => ({ ...prev, [row.deviceId]: res?.result ?? 'NO_ADDRESS' }))
    } catch (e) {
      setError(e instanceof Error ? e.message : '연결 확인에 실패했습니다')
      setChecks((prev) => {
        const next = { ...prev }
        delete next[row.deviceId]
        return next
      })
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
        기준 정보만 관리합니다. 단말종류(POS/KDS/DID)를 등록해두면 나중에 Device Server가 이
        목록으로 handshake를 확인할 수 있습니다. 접속 상태 자체는 Device Server의 접속
        현황이 정확한 답이고, IP/PORT 연결 확인은 그 주소에 뭔가 응답하는지 보는 보조 확인입니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}
      {pairingError && <div className="error" role="alert">페어링 상태: {pairingError}</div>}

      {issued && (
        <div className="panel" role="status">
          <h2>{issued.deviceId} 페어링 코드</h2>
          <p style={{ fontSize: '2.4rem', letterSpacing: '0.3em', fontWeight: 700, margin: '8px 0' }}>
            {issued.code}
          </p>
          <p className="muted">
            단말 첫 화면에 이 코드를 넣으십시오. {hhmm(issued.expiresAt)} 까지, 한 번만 쓸 수 있습니다.
            이 창을 닫으면 코드는 다시 볼 수 없습니다 — 필요하면 새로 발급하십시오(앞의 코드는 끝납니다).
          </p>
          <button className="btn" onClick={() => setIssued(null)}>닫기</button>
        </div>
      )}

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
            <label htmlFor="terminalType">단말종류</label>
            <select id="terminalType" value={form.terminalType ?? ''}
                    onChange={(e) => setForm({
                      ...form, terminalType: (e.target.value || null) as TerminalType | null
                    })}>
              <option value="">해당 없음 (일반 장비)</option>
              <option value="POS">POS</option>
              <option value="KDS">KDS</option>
              <option value="DID">DID</option>
              <option value="KIOSK">KIOSK — 캐셔 없는 POS (SunMoon Store 앱)</option>
            </select>
          </div>
          {form.terminalType === 'KDS' && (
            <div className="field">
              <label htmlFor="kdsRole">KDS 역할</label>
              <select id="kdsRole" value={form.kdsRole ?? 'SUB'}
                      onChange={(e) => setForm({ ...form, kdsRole: e.target.value as 'MAIN' | 'SUB' })}>
                <option value="SUB">SUB — 자기 메뉴만 조리</option>
                <option value="MAIN">MAIN — 주방의 중심 (매장에 하나)</option>
              </select>
              <p className="muted">
                역할은 페어링할 때 기기에 묶입니다. 바꾸면 그 기기는 다시 페어링해야 합니다.
              </p>
            </div>
          )}
          <div className="field">
            <label htmlFor="storeId">매장</label>
            <select id="storeId" value={form.storeId ?? ''}
                    onChange={(e) => setForm({ ...form, storeId: e.target.value || null })}>
              <option value="">해당 없음</option>
              {stores.map((s) => (
                <option key={s.storeId} value={s.storeId}>{s.name}</option>
              ))}
            </select>
            {form.terminalType && !form.storeId && (
              <p className="muted">
                단말종류가 있으면 매장을 지정해야 단말 현황에서 접속 중인 화면과 맞춰집니다.
              </p>
            )}
          </div>
          <div className="field">
            <label htmlFor="location">위치</label>
            <input id="location" type="text" value={form.location ?? ''}
                   onChange={(e) => setForm({ ...form, location: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="ipAddress">IP</label>
            <input id="ipAddress" type="text" placeholder="192.168.0.50" value={form.ipAddress ?? ''}
                   onChange={(e) => setForm({ ...form, ipAddress: e.target.value })} />
          </div>
          <div className="field">
            <label htmlFor="port">PORT</label>
            <input id="port" type="number" min={1} max={65535} value={form.port ?? ''}
                   onChange={(e) => setForm({
                     ...form, port: e.target.value === '' ? null : Number(e.target.value)
                   })} />
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
                <tr>
                  <th>장비 ID</th><th>이름</th><th>유형</th><th>단말종류</th><th>매장</th>
                  <th>위치</th><th>IP:PORT</th><th>사용</th><th>페어링</th><th /><th />
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const check = checks[row.deviceId]
                  return (
                    <tr key={row.deviceId}>
                      <td className="muted">{row.deviceId}</td>
                      <td>{row.name}</td>
                      <td>{row.deviceType}</td>
                      <td className="muted">
                        {row.terminalType ?? ''}
                        {row.terminalType === 'KDS' && (
                          <> {row.kdsRole === 'MAIN' ? <span className="badge on">MAIN</span> : 'SUB'}</>
                        )}
                      </td>
                      <td className="muted">
                        {row.storeId
                          ? stores.find((s) => s.storeId === row.storeId)?.name ?? row.storeId
                          : ''}
                      </td>
                      <td className="muted">{row.location ?? ''}</td>
                      <td className="muted">
                        {row.ipAddress && row.port ? `${row.ipAddress}:${row.port}` : ''}
                      </td>
                      <td>{row.active ? <span className="badge on">사용</span> : <span className="badge off">미사용</span>}</td>
                      <td className="actions">
                        {row.terminalType && row.storeId ? (() => {
                          const p = pairing[pairingKey(row.storeId, row.deviceId, row.terminalType)]
                          const roleChanged = p?.state === 'PAIRED' && row.terminalType === 'KDS'
                            && (p.kdsRole ?? 'SUB') !== (row.kdsRole ?? 'SUB')
                          const seen = p?.lastSeen ? `마지막 접속 ${new Date(p.lastSeen).toLocaleString('ko-KR')}` : '아직 접속 전'
                          return (
                            <div className="row" style={{ justifyContent: 'flex-end', alignItems: 'center' }}>
                              {p?.state === 'PAIRED' ? (
                                <span className="muted" title={seen}>
                                  페어링됨{roleChanged && <strong> · 역할 바뀜, 다시 페어링</strong>}
                                </span>
                              ) : p?.state === 'CODE_ISSUED' ? (
                                <span className="muted">코드 발급됨 (~{hhmm(p.codeExpiresAt)})</span>
                              ) : (
                                <span className="muted">안 됨</span>
                              )}
                              <button className="btn" disabled={!maySave || !row.active}
                                      onClick={() => void issueCode(row)}>코드</button>
                              {p?.state === 'PAIRED' && (
                                <button className="btn danger" disabled={!maySave}
                                        onClick={() => void unpair(row)}>해제</button>
                              )}
                            </div>
                          )
                        })() : <span className="muted">—</span>}
                      </td>
                      <td className="actions">
                        <button className="btn" disabled={check === 'checking'}
                                onClick={() => void checkConnection(row)}>
                          {check ? CHECK_LABEL[check] : '연결 확인'}
                        </button>
                      </td>
                      <td className="actions">
                        <div className="row" style={{ justifyContent: 'flex-end' }}>
                          <button className="btn" disabled={!maySave}
                                  onClick={() => { setForm(row); setEditing({ mode: 'edit' }) }}>수정</button>
                          <button className="btn danger" disabled={!mayDelete}
                                  onClick={() => void remove(row)}>삭제</button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}
