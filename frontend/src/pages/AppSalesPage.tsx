import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiGet, type AppOrderRow, type Me, type Store } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * APP 매출 조회 — 손님 쪽으로 들어온 주문을 **어디서 와서 어디로 갔는가**로
 * 봅니다.
 *
 * **읽는 곳이 APP-SERVER의 주문 원장입니다** (umbrella docs/adr/0012).
 * 전에는 OMS의 매출 내역을 읽고 "테이블 번호가 있는가"로 QR 주문을
 * 짐작했는데, 그건 도착지이지 유입 경로가 아니었습니다.
 *
 * 그래서 두 가지가 달라집니다:
 *
 *   - 채널이 짐작이 아니라 기록입니다.
 *   - **OMS가 거절한 주문도 보입니다.** 손님에게는 그것도 일어난
 *     일인데, 매출 내역에는 아예 없었습니다.
 */

const MONEY = new Intl.NumberFormat('ko-KR')

/** 원장의 상태 — 손님의 요청이 어떻게 됐는가. OMS의 상태가 아닙니다. */
const STATUS_LABEL: Record<string, string> = {
  CREATED: '접수 중',
  SENT: '매장 전달됨',
  REJECTED: '매장 거절',
  SEND_FAILED: '전달 실패',
  CANCELLED: '손님 취소'
}

/** 전달 실패는 재처리 대상이라 눈에 걸려야 합니다. */
const STATUS_TONE: Record<string, string> = {
  CREATED: '',
  SENT: 'on',
  REJECTED: 'off',
  SEND_FAILED: 'warn',
  CANCELLED: 'off'
}

const CHANNEL_LABEL: Record<string, string> = {
  APP: '앱',
  QR: '테이블 QR',
  EXTERNAL: '외부 채널'
}

/** 매장에 일감이 생긴 주문만 금액으로 셉니다 — 거절·실패·취소는 매출이 아닙니다. */
const COUNTS_AS_SALES = new Set(['SENT'])

const DIGITS_ONLY = /^\d+$/

/** 매장의 달력, 브라우저의 달력이 아닙니다 — 판매일자는 Asia/Seoul 날짜입니다. */
function seoulToday(): string {
  return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)
}

/** 자리 이름. 번호뿐이면 "번"을 붙이고, 2층부터 층을 앞에 답니다 — 단말과 같은 규칙. */
function seatLabel(order: AppOrderRow): string | null {
  if (order.tableNo === null) return null
  const name = DIGITS_ONLY.test(order.tableNo) ? order.tableNo + '번' : order.tableNo
  return order.floor !== null && order.floor > 1 ? order.floor + '층 ' + name : name
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString('ko-KR', { timeStyle: 'medium' })
}

export default function AppSalesPage({ me }: { me: Me }) {
  const today = useMemo(seoulToday, [])
  const [businessDate, setBusinessDate] = useState(today)
  const [storeId, setStoreId] = useState('')
  const [channel, setChannel] = useState('')
  const [stores, setStores] = useState<Store[]>([])
  const [rows, setRows] = useState<AppOrderRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const mayView = can(me, 'APP_SALES', 'VIEW')
  const mayReadStores = can(me, 'STORE', 'VIEW')

  useEffect(() => {
    if (!mayReadStores) return
    apiGet<Store[]>('/stores').then(setStores).catch(() => setStores([]))
  }, [mayReadStores])

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      const q = new URLSearchParams({ businessDate })
      if (storeId) q.set('storeId', storeId)
      // 거르는 일은 원장이 합니다 — 화면이 "전부 받아서 몇 개만 그리는"
      // 모양이 되면 건수와 합계가 화면마다 달라집니다.
      if (channel) q.set('channel', channel)
      setRows(await apiGet<AppOrderRow[]>('/app-sales/orders?' + q))
      setError(null)
    } catch (e) {
      // 502는 APP-SERVER가 내려간 것이지 그날 주문이 없다는 뜻이
      // 아닙니다 — 서버가 주는 메시지가 어느 서비스인지 말해줍니다.
      setError(e instanceof Error ? e.message : '주문을 불러오지 못했습니다')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [mayView, businessDate, storeId, channel])

  useEffect(() => {
    void load()
  }, [load])

  const totals = rows.reduce(
    (acc, row) => ({
      orders: acc.orders + 1,
      qr: acc.qr + (row.channel === 'QR' ? 1 : 0),
      failed: acc.failed + (row.status === 'SEND_FAILED' ? 1 : 0),
      amount: acc.amount + (COUNTS_AS_SALES.has(row.status) ? row.totalAmount : 0)
    }),
    { orders: 0, qr: 0, failed: 0, amount: 0 }
  )

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>APP 매출 조회</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head"><h1>APP 매출 조회</h1></div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        APP·QR로 들어온 주문을 한 건씩 봅니다 — 어느 경로로 왔고, 어느 자리로 갔고, 수령번호가
        무엇이었는지. <strong>매장에 전달되지 못한 주문도 함께 나옵니다</strong> — 손님에게는
        그것도 일어난 일인데 매출에는 잡히지 않습니다. 하루 전체를 일자별로 접어 보시려면
        POS 쪽 <strong>매출 조회</strong>입니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="panel">
        <div className="row">
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="date">판매일자</label>
            <input id="date" type="date" value={businessDate}
                   onChange={(e) => setBusinessDate(e.target.value)} />
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="store">매장</label>
            <select id="store" value={storeId} onChange={(e) => setStoreId(e.target.value)}>
              <option value="">전체</option>
              {stores.map((s) => (
                <option key={s.storeId} value={s.storeId}>{s.name}</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="channel">유입 경로</label>
            <select id="channel" value={channel} onChange={(e) => setChannel(e.target.value)}>
              <option value="">전체</option>
              <option value="QR">테이블 QR</option>
              <option value="APP">앱 (매장 선택)</option>
            </select>
          </div>
          <button className="btn" onClick={() => void load()} disabled={loading}>
            {loading ? '조회 중…' : '조회'}
          </button>
          {businessDate !== today && <span className="badge off">오늘이 아닙니다</span>}
        </div>
      </div>

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">그 날 들어온 주문이 없습니다.</div>
        ) : (
          <>
            <div className="row" style={{ marginBottom: 14 }}>
              <span className="badge">주문 {MONEY.format(totals.orders)}건</span>
              <span className="badge on">테이블 QR {MONEY.format(totals.qr)}건</span>
              {/* 전달 실패는 재처리 대상입니다 — 0건이면 굳이 안 그립니다. */}
              {totals.failed > 0 && (
                <span className="badge warn">전달 실패 {MONEY.format(totals.failed)}건</span>
              )}
              <span className="badge">매장 전달 합계 {MONEY.format(totals.amount)}원</span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>주문번호</th><th>경로</th><th>자리</th><th>메뉴</th>
                    <th className="num">금액</th><th>상태</th>
                    <th>수령번호</th><th>시각</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id}>
                      <td className="muted">{row.orderNo}</td>
                      <td>{CHANNEL_LABEL[row.channel] ?? row.channel}</td>
                      <td>
                        {seatLabel(row)
                          ? <span className="badge on">{seatLabel(row)}</span>
                          : <span className="muted">테이블 아님</span>}
                      </td>
                      {/* 첫 줄만 보여주던 자리입니다. 장바구니가 붙은
                          뒤로는 나머지를 버리는 셈이었습니다 — 원장에는
                          세 줄이 있는데 화면에는 한 줄만 있었습니다. */}
                      <td>
                        {(row.lines ?? []).length === 0 ? '' : (
                          <ul className="line-list">
                            {row.lines.map((line) => (
                              <li key={line.detailSeq}>
                                {line.menuName}
                                {line.quantity > 1 && <span className="muted"> × {line.quantity}</span>}
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                      <td className="num">{MONEY.format(row.totalAmount)}</td>
                      <td>
                        <span className={`badge ${STATUS_TONE[row.status] ?? ''}`}>
                          {STATUS_LABEL[row.status] ?? row.status}
                        </span>
                      </td>
                      <td className="muted">{row.pickupNo ?? ''}</td>
                      <td className="muted">{formatTime(row.placedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </>
  )
}
