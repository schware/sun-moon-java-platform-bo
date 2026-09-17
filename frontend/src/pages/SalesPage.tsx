import { Fragment, useCallback, useEffect, useMemo, useState } from 'react'
import { apiGet, type Me, type SalesOrder, type SalesSummary, type Store } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 매출 조회 — read-only, and read out of the Order service.
 *
 * BO does not hold 매출. Order stamps a 매출일자 on every order and counts
 * them; this screen asks for that count and adds the one thing Order has
 * no way to know — which shop `store-07` is.
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
  COMPLETED: '완료'
}

/** The default range the server would pick anyway, mirrored so the inputs are not empty. */
function defaultRange(): { from: string; to: string } {
  // The shop's calendar, not the browser's: a 매출일자 is an Asia/Seoul
  // date, and an operator abroad must not see a different default range
  // from the one the server counts on.
  const seoul = new Date(Date.now() + 9 * 3600 * 1000)
  const to = seoul.toISOString().slice(0, 10)
  const from = new Date(seoul.getTime() - 13 * 86400 * 1000).toISOString().slice(0, 10)
  return { from, to }
}

export default function SalesPage({ me }: { me: Me }) {
  const initial = useMemo(defaultRange, [])
  const [from, setFrom] = useState(initial.from)
  const [to, setTo] = useState(initial.to)
  const [storeId, setStoreId] = useState('')
  const [stores, setStores] = useState<Store[]>([])
  const [rows, setRows] = useState<SalesSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [openRow, setOpenRow] = useState<string | null>(null)
  const [detail, setDetail] = useState<SalesOrder[]>([])
  const [detailError, setDetailError] = useState<string | null>(null)

  const mayView = can(me, 'SALES', 'VIEW')
  const mayReadStores = can(me, 'STORE', 'VIEW')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    setOpenRow(null)
    try {
      const q = new URLSearchParams({ from, to })
      if (storeId) q.set('storeId', storeId)
      setRows(await apiGet<SalesSummary[]>(`/sales?${q}`))
      setError(null)
    } catch (e) {
      // A 502 here says Order is down, not that the day had no sales —
      // the message from the server names which service.
      setError(e instanceof Error ? e.message : '매출을 불러오지 못했습니다')
      setRows([])
    } finally {
      setLoading(false)
    }
  }, [mayView, from, to, storeId])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!mayReadStores) return
    apiGet<Store[]>('/stores').then(setStores).catch(() => setStores([]))
  }, [mayReadStores])

  async function toggle(row: SalesSummary) {
    const key = `${row.businessDate}/${row.storeId}`
    if (openRow === key) {
      setOpenRow(null)
      return
    }
    setOpenRow(key)
    setDetail([])
    setDetailError(null)
    try {
      const q = new URLSearchParams({ businessDate: row.businessDate, storeId: row.storeId })
      setDetail(await apiGet<SalesOrder[]>(`/sales/orders?${q}`))
    } catch (e) {
      setDetailError(e instanceof Error ? e.message : '내역을 불러오지 못했습니다')
    }
  }

  const totals = rows.reduce(
    (acc, row) => ({
      orders: acc.orders + row.orderCount,
      accepted: acc.accepted + row.acceptedCount,
      amount: acc.amount + row.salesAmount
    }),
    { orders: 0, accepted: 0, amount: 0 }
  )

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>매출 조회</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head"><h1>매출 조회</h1></div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: '18px' }}>
        영업일자 기준입니다 — 자정을 넘겨 장사한 주문도 그날 매출로 잡힙니다. 금액은 수락된
        주문만 셉니다. 거절과 미응답은 매출이 아니지만 그날 무슨 일이 있었는지는 알아야 하므로
        건수로 따로 보여줍니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="panel">
        <div className="row">
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="from">시작 영업일자</label>
            <input id="from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="to">종료 영업일자</label>
            <input id="to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
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
          <button className="btn" onClick={() => void load()} disabled={loading}>
            {loading ? '조회 중…' : '조회'}
          </button>
        </div>
      </div>

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : rows.length === 0 ? (
          <div className="empty">그 기간에 매출이 없습니다.</div>
        ) : (
          <>
            <div className="row" style={{ marginBottom: 14 }}>
              <span className="badge">주문 {MONEY.format(totals.orders)}건</span>
              <span className="badge on">수락 {MONEY.format(totals.accepted)}건</span>
              <span className="badge">합계 {MONEY.format(totals.amount)}원</span>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>영업일자</th><th>매장</th>
                    <th className="num">주문</th><th className="num">수락</th>
                    <th className="num">거절</th><th className="num">미응답</th>
                    <th className="num">매출액</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => {
                    const key = `${row.businessDate}/${row.storeId}`
                    const open = openRow === key
                    return (
                      <Fragment key={key}>
                        <tr className={`clickable${open ? ' active' : ''}`}
                            onClick={() => void toggle(row)}>
                          <td>{row.businessDate}</td>
                          <td>
                            {row.storeName ?? <span className="badge off">미등록 매장</span>}
                            <div className="muted">{row.storeId}</div>
                          </td>
                          <td className="num">{MONEY.format(row.orderCount)}</td>
                          <td className="num">{MONEY.format(row.acceptedCount)}</td>
                          <td className="num">{row.rejectedCount > 0 ? MONEY.format(row.rejectedCount) : ''}</td>
                          <td className="num">{row.expiredCount > 0 ? MONEY.format(row.expiredCount) : ''}</td>
                          <td className="num strong">{MONEY.format(row.salesAmount)}</td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={7} className="detail-cell">
                              {detailError ? (
                                <div className="error">{detailError}</div>
                              ) : detail.length === 0 ? (
                                <div className="empty">불러오는 중…</div>
                              ) : (
                                <table>
                                  <thead>
                                    <tr>
                                      <th>주문번호</th><th>고객</th><th>메뉴</th>
                                      <th className="num">금액</th><th>상태</th><th>수락 단말</th>
                                      <th>수령</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {detail.map((order) => (
                                      <tr key={order.id}>
                                        <td className="muted">{order.id}</td>
                                        <td className="muted">{order.customerId}</td>
                                        <td>{order.menuName}</td>
                                        <td className="num">{MONEY.format(order.amount)}</td>
                                        <td>{STATUS_LABEL[order.status] ?? order.status}</td>
                                        <td className="muted">{order.acceptedBy ?? ''}</td>
                                        {/*
                                          번호를 확인하고 내줬는가.

                                          기록만 하고 볼 곳이 없으면 기록하지
                                          않은 것과 같아서 여기까지 끌고
                                          옵니다. 정상 수령은 조용합니다 —
                                          모든 줄에 초록 배지를 달면 정작
                                          봐야 할 노란 줄이 안 보입니다.
                                        */}
                                        <td>
                                          {order.pickupOverride ? (
                                            <span className="badge warn" title={order.pickupBy ?? ''}>
                                              번호 확인 없음
                                            </span>
                                          ) : order.pickupBy ? (
                                            <span className="muted">{order.pickupBy}</span>
                                          ) : ''}
                                        </td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              )}
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </>
  )
}
