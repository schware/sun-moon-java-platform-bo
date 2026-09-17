import { useCallback, useEffect, useMemo, useState } from 'react'
import { apiGet, type Me, type SalesOrder, type Store } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * APP 매출 조회 — 손님 쪽으로 들어온 주문을 **어디서 와서 어디로 갔는가**로
 * 봅니다.
 *
 * POS 매출 조회와 같은 데이터를 읽습니다. 따로 있는 이유는 묻는 질문이
 * 다르기 때문입니다: 저쪽은 "하루에 얼마를 팔았나"라서 일자×매장으로
 * 접어 보고, 여기는 "그 주문이 몇 번 자리로 갔고 수령번호가 무엇이었나"
 * 라서 한 건씩 펼쳐 봅니다.
 *
 * 지금 갈라볼 수 있는 것은 **테이블 QR로 들어왔는가** 하나뿐입니다.
 * 주문에 "어느 경로로 들어왔나"를 적는 칸이 아직 없고, POS가 직접
 * 주문을 만들기 시작하면 그때 진짜 채널 칸이 필요해집니다.
 */

const MONEY = new Intl.NumberFormat('ko-KR')

const STATUS_LABEL: Record<string, string> = {
  PLACED: '접수 대기',
  ACCEPTED: '수락',
  REJECTED: '거절',
  EXPIRED: '미응답',
  COOKING: '조리 중',
  PRODUCED: '조리 완료',
  DELIVERING: '배달 중',
  COMPLETED: '수령 완료'
}

/** Order가 매출로 세는 것과 같은 상태들 — 거절·미응답은 매출이 아닙니다. */
const COUNTS_AS_SALES = new Set(['ACCEPTED', 'COOKING', 'PRODUCED', 'DELIVERING', 'COMPLETED'])

type Origin = 'all' | 'table' | 'other'

const DIGITS_ONLY = /^\d+$/

/** 매장의 달력, 브라우저의 달력이 아닙니다 — 판매일자는 Asia/Seoul 날짜입니다. */
function seoulToday(): string {
  return new Date(Date.now() + 9 * 3600 * 1000).toISOString().slice(0, 10)
}

/** 자리 이름. 번호뿐이면 "번"을 붙이고, 2층부터 층을 앞에 답니다 — 단말과 같은 규칙. */
function seatLabel(order: SalesOrder): string | null {
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
  const [origin, setOrigin] = useState<Origin>('all')
  const [stores, setStores] = useState<Store[]>([])
  const [rows, setRows] = useState<SalesOrder[]>([])
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
      // 거르는 일은 서버가 합니다 — 화면이 "전부 받아서 세 개만 그리는"
      // 모양이 되면 건수와 합계가 화면마다 달라집니다.
      if (origin !== 'all') q.set('fromTable', String(origin === 'table'))
      setRows(await apiGet<SalesOrder[]>('/app-sales/orders?' + q))
      setError(null)
    } catch (e) {
      // 502는 Order가 내려간 것이지 그날 주문이 없다는 뜻이 아닙니다 —
      // 서버가 주는 메시지가 어느 서비스인지 말해줍니다.
      setError(e instanceof Error ? e.message : '주문을 불러오지 못했습니다')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [mayView, businessDate, storeId, origin])

  useEffect(() => {
    void load()
  }, [load])

  const totals = rows.reduce(
    (acc, row) => ({
      orders: acc.orders + 1,
      table: acc.table + (row.tableNo !== null ? 1 : 0),
      amount: acc.amount + (COUNTS_AS_SALES.has(row.status) ? row.amount : 0)
    }),
    { orders: 0, table: 0, amount: 0 }
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
        테이블 QR과 손님 앱으로 들어온 주문을 한 건씩 봅니다 — 어느 자리로 갔고, 수령번호가
        무엇이었고, 어느 QR로 들어왔는지. 금액은 <strong>수락된 주문만</strong> 셉니다.
        하루 전체를 일자별로 접어 보시려면 POS 쪽 <strong>매출 조회</strong>입니다.
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
            <label htmlFor="origin">구분</label>
            <select id="origin" value={origin} onChange={(e) => setOrigin(e.target.value as Origin)}>
              <option value="all">전체</option>
              <option value="table">테이블 QR 주문</option>
              <option value="other">그 밖 (매장 선택)</option>
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
              <span className="badge on">테이블 {MONEY.format(totals.table)}건</span>
              <span className="badge">합계 {MONEY.format(totals.amount)}원</span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>수령번호</th><th>자리</th><th>메뉴</th>
                    <th className="num">금액</th><th>상태</th>
                    <th>QR번호</th><th>거래번호</th><th>시각</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.id}>
                      {/* 수령번호가 없는 옛 주문은 주문번호가 그 자리를
                          맡습니다 — 빈 칸은 누락처럼 읽힙니다. */}
                      <td>{row.pickupNo ?? <span className="muted">#{row.id}</span>}</td>
                      <td>
                        {seatLabel(row)
                          ? <span className="badge on">{seatLabel(row)}</span>
                          : <span className="muted">테이블 아님</span>}
                      </td>
                      <td>{row.menuName}</td>
                      <td className="num">{MONEY.format(row.amount)}</td>
                      <td>
                        <span className={`status ${row.status.toLowerCase()}`}>
                          {STATUS_LABEL[row.status] ?? row.status}
                        </span>
                      </td>
                      <td className="muted">{row.qrCode ?? ''}</td>
                      <td className="muted">{row.tranNo ?? ''}</td>
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
