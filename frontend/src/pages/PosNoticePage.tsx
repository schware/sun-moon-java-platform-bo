import { useCallback, useEffect, useState } from 'react'
import { apiGet, apiWrite, type Me, type PosNotice, type Store } from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 단말 공지·긴급 메시지 (업무 정의서 §3.5).
 *
 * BO의 공지사항(BOARD)과 **다른 화면**입니다. 저쪽은 BO 안에서 운영자끼리
 * 보는 게시판이고, 여기는 매장 단말 화면에 뜨는 글입니다 — 긴급이면 손님
 * 앞에서 일하는 사람의 화면을 가로챕니다. 그래서 권한도 따로입니다.
 *
 * 화면이 지키는 사실 하나: **미는 것은 덤이고 남기는 것이 본체입니다.**
 * 등록하면 먼저 적히고, 긴급이면 그 다음에 밀어 넣습니다. `pushed`가
 * 0이어도 실패가 아닙니다 — 꺼져 있던 장비는 켜질 때 조회로 봅니다.
 */

function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }) : ''
}

const EMPTY = { storeId: '', deviceType: '', title: '', body: '', urgent: false, endsAt: '' }

export default function PosNoticePage({ me }: { me: Me }) {
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [notices, setNotices] = useState<PosNotice[]>([])
  const [form, setForm] = useState(EMPTY)
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const mayView = can(me, 'POS_NOTICE', 'VIEW')
  const mayPublish = can(me, 'POS_NOTICE', 'CREATE')

  useEffect(() => {
    if (!can(me, 'STORE', 'VIEW')) return
    apiGet<Store[]>('/stores').then(setStores).catch(() => setStores([]))
  }, [me])

  const load = useCallback(async () => {
    if (!mayView) return
    setLoading(true)
    try {
      const q = storeId ? `?${new URLSearchParams({ storeId })}` : ''
      const answer = await apiGet<{ notices: PosNotice[] }>(`/pos-ops/notices${q}`)
      setNotices(answer.notices ?? [])
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '공지를 불러오지 못했습니다')
      setNotices([])
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId])

  useEffect(() => {
    void load()
  }, [load])

  async function publish(e: React.FormEvent) {
    e.preventDefault()
    if (!mayPublish) return
    setBusy(true)
    setError(null)
    try {
      const answer = await apiWrite<{ id: number; pushed: number; urgent: boolean }>(
        'POST', '/pos-ops/notices',
        {
          storeId: form.storeId || null,
          deviceType: form.deviceType || null,
          title: form.title,
          body: form.body,
          urgent: form.urgent,
          endsAt: form.endsAt ? new Date(form.endsAt).toISOString() : null
        })
      // pushed 를 그대로 말해 줍니다. 긴급을 보낸 사람은 그 자리에서
      // "지금 몇 대가 봤나"를 알아야 하고, 0이 실패가 아니라는 것도
      // 함께 알아야 합니다.
      setNotice(answer?.urgent
        ? `긴급 메시지를 걸었습니다 — 지금 붙어 있는 단말 ${answer.pushed}대에 바로 전달했습니다.` +
          (answer.pushed === 0 ? ' (붙어 있는 단말이 없습니다. 켜질 때 조회로 봅니다.)' : '')
        : '공지를 걸었습니다. 단말이 다음에 물어볼 때 받습니다.')
      setForm(EMPTY)
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : '공지를 걸지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  if (!mayView) return <div className="empty">이 화면에 대한 권한이 없습니다.</div>

  const active = notices.filter((n) => n.active).length
  const urgent = notices.filter((n) => n.urgent && n.active).length

  return (
    <>
      <div className="page-head">
        <h1>단말 공지</h1>
        <button className="btn" onClick={() => void load()}>새로고침</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: 18 }}>
        <strong>매장 단말 화면에 뜨는 글</strong>입니다 — BO 안에서 보는 공지사항과 다릅니다.
        긴급으로 걸면 지금 붙어 있는 단말에 바로 밀어 넣고, 동시에 표에도 남깁니다.
        밀기만 하면 그 순간 꺼져 있던 장비는 영영 모르기 때문입니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      {mayPublish && (
        <div className="panel">
          <h2 style={{ fontSize: 15, margin: '0 0 14px' }}>새 공지</h2>
          <form onSubmit={publish}>
            <div className="row" style={{ marginBottom: 10 }}>
              <select
                value={form.storeId}
                onChange={(e) => setForm({ ...form, storeId: e.target.value })}
              >
                <option value="">전 매장</option>
                {stores.map((s) => <option key={s.storeId} value={s.storeId}>{s.name}</option>)}
              </select>
              <select
                value={form.deviceType}
                onChange={(e) => setForm({ ...form, deviceType: e.target.value })}
              >
                <option value="">전 장비</option>
                <option value="POS">POS</option>
                <option value="KDS">KDS</option>
                <option value="KIOSK">KIOSK</option>
              </select>
              <input
                required
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="제목"
                style={{ flex: 1, minWidth: 220 }}
              />
            </div>
            <div style={{ marginBottom: 10 }}>
              <textarea
                required
                value={form.body}
                onChange={(e) => setForm({ ...form, body: e.target.value })}
                placeholder="내용"
                rows={3}
                style={{ width: '100%' }}
              />
            </div>
            <div className="row">
              <label className="row" style={{ gap: 6 }}>
                <span className="muted" style={{ fontSize: 12.5 }}>끝나는 때 (비우면 계속)</span>
                <input
                  type="datetime-local"
                  value={form.endsAt}
                  onChange={(e) => setForm({ ...form, endsAt: e.target.value })}
                />
              </label>
              <label className="row" style={{ gap: 6 }}>
                <input
                  type="checkbox"
                  checked={form.urgent}
                  onChange={(e) => setForm({ ...form, urgent: e.target.checked })}
                />
                <span>긴급 — 지금 바로 단말 화면에 띄웁니다</span>
              </label>
              <button className={form.urgent ? 'btn danger' : 'btn primary'} disabled={busy}>
                {form.urgent ? '긴급 전송' : '공지 등록'}
              </button>
            </div>
          </form>
          {form.urgent && (
            <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
              긴급은 손님 앞에서 일하는 사람의 화면을 가로챕니다. 누가 걸었는지는 로그인한
              계정으로 <strong>서버가 적습니다</strong> — 화면이 보내는 이름은 쓰지 않습니다.
            </p>
          )}
        </div>
      )}

      <div className="panel" style={{ marginTop: 16 }}>
        <div className="row" style={{ marginBottom: 14 }}>
          <select value={storeId} onChange={(e) => setStoreId(e.target.value)}>
            <option value="">전체</option>
            {stores.map((s) => <option key={s.storeId} value={s.storeId}>{s.name}</option>)}
          </select>
          <span className="badge on">걸려 있음 {active}</span>
          {urgent > 0 && <span className="badge down">긴급 {urgent}</span>}
        </div>

        {loading && notices.length === 0 ? (
          <div className="empty">불러오는 중…</div>
        ) : notices.length === 0 ? (
          <div className="empty">아직 건 공지가 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>상태</th><th>대상</th><th>제목</th><th>내용</th>
                  <th>기간</th><th>확인</th><th>건 사람</th>
                </tr>
              </thead>
              <tbody>
                {notices.map((row) => (
                  <tr key={row.id}>
                    <td>
                      {row.urgent && <span className="badge down">긴급</span>}
                      {!row.urgent && row.active && <span className="badge on">공지</span>}
                      {!row.active && <span className="badge off">지남</span>}
                    </td>
                    <td>
                      {row.scope === 'ALL_STORES'
                        ? <span className="badge">전 매장</span>
                        : row.scope}
                      {row.deviceType && <div className="muted">{row.deviceType}</div>}
                    </td>
                    <td>{row.title}</td>
                    <td className="muted" style={{ maxWidth: 320 }}>{row.body}</td>
                    <td className="muted">
                      {when(row.startsAt)}
                      {row.endsAt ? ` ~ ${when(row.endsAt)}` : ' ~'}
                    </td>
                    <td>
                      {/* 긴급만 셉니다. 일반 공지는 아무도 확인하지 않고,
                          0이라는 숫자가 붙으면 문제처럼 보입니다. */}
                      {row.urgent ? (
                        row.acknowledged ? (
                          <span className="badge on" title={(row.acknowledgedBy ?? []).join(', ')}>
                            {row.acknowledged}대 확인
                          </span>
                        ) : (
                          <span className="badge warn">확인 없음</span>
                        )
                      ) : (
                        <span className="muted">—</span>
                      )}
                    </td>
                    <td className="muted">{row.createdBy ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <p className="muted" style={{ marginTop: 14 }}>
        지난 공지도 지우지 않습니다 — "그때 뭐라고 붙었었나"의 답이기 때문입니다.
        긴급의 <strong>확인 없음</strong>은 그 메시지를 아직 아무 단말도 열어보지 않았다는
        뜻이고, 그때 전화를 걸 곳을 좁혀 주는 것이 이 칸의 일입니다.
      </p>
    </>
  )
}
