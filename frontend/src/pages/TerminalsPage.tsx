import { useCallback, useEffect, useState } from 'react'
import { apiGet, type Me, type Store, type TerminalStatus } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 단말 현황 — BO's 장비 master next to what the Device Server can see.
 *
 * Two services own two halves of one question: BO decides what should
 * exist, the Device Server knows what does. This screen is where they are
 * put side by side, and the disagreements are the point — a registered
 * terminal that is not connected is a dark counter, and a connected
 * terminal nobody registered is a screen that got in without being on the
 * list.
 */

/** Long enough that a reload is a deliberate act, short enough to be current. */
const REFRESH_MS = 15000

function elapsed(since: string): string {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(since).getTime()) / 1000))
  if (seconds < 60) return `${seconds}초`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}분`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}시간`
  return `${Math.floor(seconds / 86400)}일`
}

export default function TerminalsPage({ me }: { me: Me }) {
  const [rows, setRows] = useState<TerminalStatus[]>([])
  const [stores, setStores] = useState<Store[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [checkedAt, setCheckedAt] = useState<Date | null>(null)

  const mayView = can(me, 'TERMINAL', 'VIEW')
  const mayReadStores = can(me, 'STORE', 'VIEW')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    try {
      setRows(await apiGet<TerminalStatus[]>('/terminals'))
      setCheckedAt(new Date())
      setError(null)
    } catch (e) {
      // Not cleared to an empty list: "아무도 안 붙어 있다" and
      // "Device Server에 물어볼 수 없었다" must not look the same.
      setError(e instanceof Error ? e.message : '단말 현황을 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [mayView])

  useEffect(() => {
    void load()
    const timer = setInterval(() => void load(), REFRESH_MS)
    return () => clearInterval(timer)
  }, [load])

  useEffect(() => {
    if (!mayReadStores) return
    apiGet<Store[]>('/stores').then(setStores).catch(() => setStores([]))
  }, [mayReadStores])

  const storeName = (id: string | null) =>
    id === null ? null : stores.find((s) => s.storeId === id)?.name ?? id

  const connected = rows.filter((r) => r.connected).length
  const dark = rows.filter((r) => r.registered && !r.connected && r.active).length
  const unknown = rows.filter((r) => !r.registered).length

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>단말 현황</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>단말 현황</h1>
        <button className="btn" onClick={() => void load()}>새로고침</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        접속 여부는 Device Server가 가진 사실이고 BO는 읽기만 합니다 — 여기에는 연결을
        끊거나 붙이는 버튼이 없습니다. {REFRESH_MS / 1000}초마다 다시 물어봅니다.
        {checkedAt && ` (마지막 확인 ${checkedAt.toLocaleTimeString('ko-KR')})`}
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="panel">
        <div className="row" style={{ marginBottom: 14 }}>
          <span className="badge on">접속 중 {connected}</span>
          {dark > 0 && <span className="badge warn">화면 꺼짐 {dark}</span>}
          {unknown > 0 && <span className="badge warn">미등록 접속 {unknown}</span>}
        </div>

        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">등록된 단말도, 접속 중인 단말도 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>상태</th><th>매장</th><th>종류</th><th>단말기번호</th>
                  <th>이름</th><th>위치</th><th>접속</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={`${row.storeId}/${row.deviceId}/${row.terminalType}`}>
                    <td>
                      {!row.registered ? (
                        <span className="badge warn">미등록</span>
                      ) : !row.active ? (
                        <span className="badge off">미사용</span>
                      ) : row.connected ? (
                        <span className="badge on">접속 중</span>
                      ) : (
                        <span className="badge off">끊김</span>
                      )}
                    </td>
                    <td>
                      {row.storeIds && row.storeIds.length > 1 ? (
                        <>
                          <span className="badge">여러 매장</span>
                          <div className="muted">
                            {row.storeIds.map((id) => storeName(id)).join(', ')}
                          </div>
                        </>
                      ) : (
                        storeName(row.storeId) ?? <span className="muted">미지정</span>
                      )}
                    </td>
                    <td>{row.terminalType}</td>
                    <td className="muted">{row.deviceId}</td>
                    <td>{row.name ?? <span className="muted">장비에 없음</span>}</td>
                    <td className="muted">{row.location ?? ''}</td>
                    <td className="muted">
                      {row.connectedAt ? `${elapsed(row.connectedAt)} 전부터` : ''}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <p className="muted" style={{ marginTop: 14 }}>
        <strong>미등록</strong>은 장비 화면에 없는 단말기번호로 접속했다는 뜻입니다. Device
        Server는 아직 형식만 확인하고 BO의 장비 목록과 대조하지 않기 때문에, 지금은 이 줄이
        그 사실을 볼 수 있는 유일한 자리입니다.
      </p>
    </>
  )
}
