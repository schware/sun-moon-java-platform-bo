import { useCallback, useEffect, useState, type FormEvent } from 'react'
import {
  apiGet, apiWrite,
  type ConnectionCheckResult, type Device, type Me, type Store, type TerminalType
} from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit' }
type CheckState = 'checking' | ConnectionCheckResult

const BLANK: Device = {
  deviceId: '', name: '', deviceType: '', location: '', storeId: null,
  terminalType: null, ipAddress: '', port: null, active: true, kdsRole: null
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

  useEffect(() => {
    void load()
    void loadTypes()
  }, [load, loadTypes])

  // KIOSK 관리자 PIN (store-app.md §5.3) — 화면에 남기지 않도록 password 칸, 보내고 나면 비웁니다.
  const [pinFor, setPinFor] = useState<Device | null>(null)
  const [pin, setPin] = useState('')
  const [pinAgain, setPinAgain] = useState('')
  const [pinNote, setPinNote] = useState<string | null>(null)

  async function savePin(event: FormEvent) {
    event.preventDefault()
    if (!pinFor) return
    if (!/^[0-9]{4,8}$/.test(pin)) {
      setPinNote('PIN 은 숫자 4~8자리입니다')
      return
    }
    if (pin !== pinAgain) {
      setPinNote('두 번 넣은 PIN 이 다릅니다')
      return
    }
    try {
      await apiWrite('POST', `/devices/${encodeURIComponent(pinFor.deviceId)}/kiosk-pin`, { pin })
      setPinNote(`${pinFor.deviceId} 의 관리자 PIN 을 정했습니다`)
      setPinFor(null)
    } catch (e) {
      setPinNote(e instanceof Error ? e.message : 'PIN 을 정하지 못했습니다')
    } finally {
      setPin('')
      setPinAgain('')
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

      {pinNote && !pinFor && <div className="muted" role="status">{pinNote}</div>}
      {pinFor && (
        <form className="panel" onSubmit={(e) => void savePin(e)}>
          <h2>{pinFor.deviceId} 관리자 PIN</h2>
          <p className="muted">
            KIOSK 화면 왼쪽 위 구석을 5번 누르면 이 PIN 을 묻습니다. 맞으면 잠금이 풀려 앱을 끄거나
            설정을 다시 할 수 있습니다. 숫자 4~8자리. BO 에는 남지 않습니다.
          </p>
          <div className="field">
            <label htmlFor="kioskPin">PIN</label>
            <input id="kioskPin" type="password" inputMode="numeric" autoComplete="new-password"
                   value={pin} onChange={(e) => setPin(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="kioskPinAgain">한 번 더</label>
            <input id="kioskPinAgain" type="password" inputMode="numeric" autoComplete="new-password"
                   value={pinAgain} onChange={(e) => setPinAgain(e.target.value)} />
          </div>
          {pinNote && <div className="error" role="alert">{pinNote}</div>}
          <div className="row">
            <button className="btn primary" type="submit">정하기</button>
            <button className="btn" type="button"
                    onClick={() => { setPinFor(null); setPin(''); setPinAgain(''); setPinNote(null) }}>취소</button>
          </div>
        </form>
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
                  <th>위치</th><th>IP:PORT</th><th>사용</th><th /><th /><th />
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
                        {row.terminalType === 'KIOSK' && (
                          <button className="btn" disabled={!maySave}
                                  onClick={() => { setPinNote(null); setPinFor(row) }}>관리자 PIN</button>
                        )}
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
