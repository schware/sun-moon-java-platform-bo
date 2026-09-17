import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  apiGet,
  apiWrite,
  type MasterPending,
  type MasterPlan,
  type MasterPlans,
  type Me,
  type ProgramStatus,
  type Store
} from '../lib/api'
import { can } from '../lib/permissions'

/**
 * 배포 현황 — 마스터(§3.6)와 프로그램(§3.5).
 *
 * 한 화면에 둔 이유: **같은 질문**에 답하기 때문입니다 — "이 장비가
 * 지금 무엇을 들고 있고, 무엇이 밀려 있나". 내용이 표인지 파일인지만
 * 다르고, 관리하는 것은 둘 다 버전과 적용 결과입니다.
 *
 * 화면 전체를 관통하는 규칙 하나: **보낸 것과 적용된 것은 다른 일입니다.**
 * 그래서 "전송됨"은 초록이 아니고, 적용 버전은 장비가 답해야 올라갑니다.
 */

const MASTER_TYPES = [
  'POS_DEVICE', 'POS_SETTING', 'MENU', 'TOUCH_KEY', 'SET', 'SET_GROUP',
  'RECEIPT', 'RECEIPT_GROUP', 'STORE', 'KITCHEN_DEVICE',
  'PROMOTION', 'PROMOTION_DETAIL', 'PAYMENT_DISCOUNT'
] as const

const MASTER_LABEL: Record<string, string> = {
  POS_DEVICE: 'POS 장비', POS_SETTING: 'POS 설정', MENU: '메뉴', TOUCH_KEY: '터치키',
  SET: '세트', SET_GROUP: '세트 그룹', RECEIPT: '영수증', RECEIPT_GROUP: '영수증 그룹',
  STORE: '점포', KITCHEN_DEVICE: '주방 Device', PROMOTION: '프로모션',
  PROMOTION_DETAIL: '프로모션 상세', PAYMENT_DISCOUNT: '결제·할인'
}

const MODE_LABEL: Record<string, string> = {
  FULL: '전체', DELTA: '변경분', UP_TO_DATE: '최신'
}

function when(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' }) : ''
}

export default function PosDeployPage({ me }: { me: Me }) {
  const [stores, setStores] = useState<Store[]>([])
  const [storeId, setStoreId] = useState('')
  const [deviceType, setDeviceType] = useState('POS')
  const [deviceNo, setDeviceNo] = useState('pos-01')

  const [plans, setPlans] = useState<MasterPlan[]>([])
  const [pending, setPending] = useState<MasterPending[]>([])
  const [programs, setPrograms] = useState<ProgramStatus | null>(null)

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState<string | null>(null)

  const [release, setRelease] = useState({
    storeId: '', deviceType: 'POS', program: '', version: '',
    packageUrl: '', checksum: '', mandatory: false, note: ''
  })

  const mayView = can(me, 'POS_DEPLOY', 'VIEW')
  const mayBump = can(me, 'POS_DEPLOY', 'SAVE')
  const mayRelease = can(me, 'POS_DEPLOY', 'CREATE')

  useEffect(() => {
    if (!can(me, 'STORE', 'VIEW')) return
    apiGet<Store[]>('/stores')
      .then((s) => {
        setStores(s)
        setStoreId((current) => (current === '' && s.length > 0 ? s[0]!.storeId : current))
      })
      .catch(() => setStores([]))
  }, [me])

  const load = useCallback(async () => {
    if (!mayView || !storeId || !deviceNo) return
    setLoading(true)
    try {
      const device = new URLSearchParams({ storeId, deviceType, deviceNo })
      const store = new URLSearchParams({ storeId })
      const [masters, waiting, programStatus] = await Promise.all([
        apiGet<MasterPlans>(`/pos-ops/masters?${device}`),
        apiGet<{ pending: MasterPending[] }>(`/pos-ops/masters/pending?${store}`),
        apiGet<ProgramStatus>(`/pos-ops/programs/status?${store}`)
      ])
      setPlans(masters.masters ?? [])
      setPending(waiting.pending ?? [])
      setPrograms(programStatus)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '배포 현황을 불러오지 못했습니다')
      setPlans([])
      setPending([])
      setPrograms(null)
    } finally {
      setLoading(false)
    }
  }, [mayView, storeId, deviceType, deviceNo])

  useEffect(() => {
    void load()
  }, [load])

  async function bump(masterType: string) {
    if (!mayBump) return
    setBusy(masterType)
    setError(null)
    try {
      const q = new URLSearchParams({ storeId, masterType })
      const answer = await apiWrite<{ version: number }>('POST', `/pos-ops/masters/version?${q}`)
      setNotice(`${MASTER_LABEL[masterType] ?? masterType} v${answer?.version} — 장비가 다음에 물어볼 때 받습니다`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '버전을 올리지 못했습니다')
    } finally {
      setBusy(null)
    }
  }

  async function publishRelease(e: React.FormEvent) {
    e.preventDefault()
    if (!mayRelease) return
    setBusy('release')
    setError(null)
    try {
      const answer = await apiWrite<{ id: number; scope: string }>(
        'POST', '/pos-ops/programs/release', release)
      setNotice(`${release.program} ${release.version} 등록 — 대상 ${answer?.scope}`)
      setRelease({ ...release, program: '', version: '', packageUrl: '', checksum: '', note: '' })
      await load()
    } catch (err) {
      setError(err instanceof Error ? err.message : '배포를 등록하지 못했습니다')
    } finally {
      setBusy(null)
    }
  }

  const masterSummary = useMemo(() => ({
    behind: plans.filter((p) => p.mode !== 'UP_TO_DATE').length,
    failed: pending.filter((p) => p.status === 'FAILED').length
  }), [plans, pending])

  if (!mayView) return <div className="empty">이 화면에 대한 권한이 없습니다.</div>

  return (
    <>
      <div className="page-head">
        <h1>배포 현황</h1>
        <button className="btn" onClick={() => void load()}>새로고침</button>
      </div>

      <p className="muted" style={{ marginTop: '-10px', marginBottom: 18 }}>
        마스터(표)와 프로그램(파일)을 한자리에서 봅니다. 규칙은 하나입니다 —
        <strong> 적용 버전은 장비가 말해야 올라갑니다</strong>. 보낸 것으로 올리면 못 받은
        장비가 최신처럼 보이고, 재전송 목록에서도 빠져 아무도 챙기지 않게 됩니다.
      </p>

      {error && <div className="error" role="alert">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div className="panel">
        <div className="row" style={{ marginBottom: 14 }}>
          <select value={storeId} onChange={(e) => setStoreId(e.target.value)}>
            {stores.map((s) => (
              <option key={s.storeId} value={s.storeId}>{s.name}</option>
            ))}
          </select>
          <select value={deviceType} onChange={(e) => setDeviceType(e.target.value)}>
            <option value="POS">POS</option>
            <option value="KIOSK">KIOSK</option>
            <option value="KDS">KDS</option>
          </select>
          <input
            value={deviceNo}
            onChange={(e) => setDeviceNo(e.target.value)}
            placeholder="장비번호 — pos-01"
            style={{ width: 150 }}
          />
          {masterSummary.behind > 0 && <span className="badge warn">마스터 밀림 {masterSummary.behind}</span>}
          {masterSummary.failed > 0 && <span className="badge down">실패 {masterSummary.failed}</span>}
        </div>
        <p className="muted" style={{ fontSize: 12, marginTop: 0 }}>
          장비 열쇠는 (매장, 장비유형, 장비번호)입니다 — 같은 번호의 POS와 KIOSK는 서로
          다른 장비입니다.
        </p>
      </div>

      {/* ---- 마스터 ---- */}
      <div className="panel" style={{ marginTop: 16 }}>
        <h2 style={{ fontSize: 15, margin: '0 0 4px' }}>마스터 (§3.6)</h2>
        <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
          내용은 여기 없습니다 — BO가 만들고 View로 내보내며, 이 표는 <strong>버전과 적용
          결과만</strong> 관리합니다. 한 벌 더 들고 있으면 둘이 어긋나는 날이 옵니다.
        </p>
        {loading && plans.length === 0 ? (
          <div className="empty">불러오는 중…</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>마스터</th><th>현재</th><th>이 장비</th><th>보낼 것</th><th>사유</th>
                  {mayBump && <th className="actions">버전</th>}
                </tr>
              </thead>
              <tbody>
                {MASTER_TYPES.map((type) => {
                  const plan = plans.find((p) => p.masterType === type)
                  return (
                    <tr key={type}>
                      <td>{MASTER_LABEL[type]}</td>
                      <td>{plan ? `v${plan.currentVersion}` : <span className="muted">미등록</span>}</td>
                      <td>
                        {plan?.appliedVersion != null
                          ? `v${plan.appliedVersion}`
                          : <span className="muted">없음</span>}
                      </td>
                      <td>
                        {!plan ? (
                          <span className="muted">—</span>
                        ) : plan.mode === 'UP_TO_DATE' ? (
                          <span className="badge on">{MODE_LABEL.UP_TO_DATE}</span>
                        ) : plan.mode === 'FULL' ? (
                          <span className="badge warn">{MODE_LABEL.FULL}</span>
                        ) : (
                          <span className="badge">{MODE_LABEL.DELTA}</span>
                        )}
                      </td>
                      <td className="muted">{plan?.lastError ?? ''}</td>
                      {mayBump && (
                        <td className="actions">
                          <button
                            className="btn"
                            disabled={busy === type || !storeId}
                            onClick={() => void bump(type)}
                          >
                            올리기
                          </button>
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
          버전을 올려도 <strong>지금 밀어붙이지 않습니다</strong>. 꺼져 있는 장비가 있기
          때문이고, 그 장비는 켜질 때 알아서 뒤처진 것을 받습니다.
        </p>
      </div>

      {pending.length > 0 && (
        <div className="panel" style={{ marginTop: 16 }}>
          <h2 style={{ fontSize: 15, margin: '0 0 4px' }}>마스터 재전송 대상</h2>
          <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
            아직 답이 없는 것(전송됨)과 장비가 실패했다고 답한 것. 전송됨이 초록이 아닌
            이유는 그게 <strong>아직 모르는 상태</strong>이기 때문입니다.
          </p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>상태</th><th>장비</th><th>마스터</th><th>적용 버전</th><th>시도</th><th>사유</th><th>보낸 때</th></tr>
              </thead>
              <tbody>
                {pending.map((row) => (
                  <tr key={`${row.deviceType}/${row.deviceNo}/${row.masterType}`}>
                    <td>
                      {row.status === 'FAILED'
                        ? <span className="badge down">실패</span>
                        : <span className="badge warn">전송됨</span>}
                    </td>
                    <td>{row.deviceType} / {row.deviceNo}</td>
                    <td>{MASTER_LABEL[row.masterType] ?? row.masterType}</td>
                    <td>{row.appliedVersion != null ? `v${row.appliedVersion}` : <span className="muted">없음</span>}</td>
                    <td>{row.attempts}</td>
                    <td className="muted">{row.lastError ?? ''}</td>
                    <td className="muted">{when(row.sentAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ---- 프로그램 ---- */}
      <div className="panel" style={{ marginTop: 16 }}>
        <h2 style={{ fontSize: 15, margin: '0 0 4px' }}>프로그램 설치 현황 (§3.5)</h2>
        <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
          중간 상태가 있습니다 — <strong>받다 실패한 것과 깔다 실패한 것은 손볼 곳이
          다릅니다</strong>. 앞은 회선이나 파일 주소이고 뒤는 장비입니다.
        </p>
        {!programs || programs.installs.length === 0 ? (
          <div className="empty">이 매장 장비가 아직 아무것도 보고하지 않았습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>상태</th><th>장비</th><th>프로그램</th><th>깔린 것</th><th>받을 것</th><th>시도</th><th>사유</th><th>설치 시각</th></tr>
              </thead>
              <tbody>
                {programs.installs.map((row) => (
                  <tr key={`${row.deviceType}/${row.deviceNo}/${row.program}`}>
                    <td>
                      {row.status === 'INSTALLED' ? <span className="badge on">설치됨</span>
                        : row.status === 'FAILED' ? <span className="badge down">실패</span>
                        : row.status === 'DOWNLOADED' ? <span className="badge">받음</span>
                        : <span className="badge warn">전달됨</span>}
                    </td>
                    <td>{row.deviceType} / {row.deviceNo}</td>
                    <td>{row.program}</td>
                    <td>{row.installedVersion ?? <span className="muted">없음</span>}</td>
                    <td className="muted">{row.targetVersion ?? ''}</td>
                    <td>{row.attempts}</td>
                    <td className="muted">{row.lastError ?? ''}</td>
                    <td className="muted">{when(row.installedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {programs && programs.releases.length > 0 && (
        <div className="panel" style={{ marginTop: 16 }}>
          <h2 style={{ fontSize: 15, margin: '0 0 4px' }}>배포 이력</h2>
          <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
            최신은 <strong>가장 나중에 낸 것</strong>입니다 — 버전을 숫자로 견주지 않기
            때문에 되돌리기가 특별한 경우가 아니라 그냥 새 배포입니다. 매장을 지정한 배포는
            전 매장 배포를 이깁니다.
          </p>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>대상</th><th>장비유형</th><th>프로그램</th><th>버전</th><th>필수</th><th>메모</th><th>낸 때</th></tr>
              </thead>
              <tbody>
                {programs.releases.map((row) => (
                  <tr key={row.id}>
                    <td>
                      {row.scope === 'ALL_STORES'
                        ? <span className="badge">전 매장</span>
                        : <span className="badge on">{row.scope}</span>}
                    </td>
                    <td>{row.deviceType}</td>
                    <td>{row.program}</td>
                    <td>{row.version}</td>
                    <td>{row.mandatory ? <span className="badge warn">필수</span> : ''}</td>
                    <td className="muted">{row.note ?? ''}</td>
                    <td className="muted">{when(row.releasedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {mayRelease && (
        <div className="panel" style={{ marginTop: 16 }}>
          <h2 style={{ fontSize: 15, margin: '0 0 4px' }}>프로그램 배포 등록</h2>
          <p className="muted" style={{ marginTop: 0, marginBottom: 14 }}>
            매장을 비우면 <strong>전 매장</strong>입니다. 한 곳만 먼저 올려 보려면 그 매장을
            고르십시오 — 매장 지정이 전 매장 배포를 이깁니다.
          </p>
          <form onSubmit={publishRelease}>
            <div className="row" style={{ marginBottom: 10 }}>
              <select
                value={release.storeId}
                onChange={(e) => setRelease({ ...release, storeId: e.target.value })}
              >
                <option value="">전 매장</option>
                {stores.map((s) => <option key={s.storeId} value={s.storeId}>{s.name}</option>)}
              </select>
              <select
                value={release.deviceType}
                onChange={(e) => setRelease({ ...release, deviceType: e.target.value })}
              >
                <option value="POS">POS</option>
                <option value="KIOSK">KIOSK</option>
                <option value="KDS">KDS</option>
              </select>
              <input
                required
                value={release.program}
                onChange={(e) => setRelease({ ...release, program: e.target.value })}
                placeholder="프로그램 — pos-main"
              />
              <input
                required
                value={release.version}
                onChange={(e) => setRelease({ ...release, version: e.target.value })}
                placeholder="버전 — 1.4.2"
                style={{ width: 130 }}
              />
            </div>
            <div className="row" style={{ marginBottom: 10 }}>
              <input
                required
                value={release.packageUrl}
                onChange={(e) => setRelease({ ...release, packageUrl: e.target.value })}
                placeholder="파일 주소"
                style={{ flex: 1, minWidth: 260 }}
              />
              <input
                value={release.checksum}
                onChange={(e) => setRelease({ ...release, checksum: e.target.value })}
                placeholder="체크섬 (권장)"
                style={{ width: 220 }}
              />
            </div>
            <div className="row">
              <input
                value={release.note}
                onChange={(e) => setRelease({ ...release, note: e.target.value })}
                placeholder="메모"
                style={{ flex: 1, minWidth: 220 }}
              />
              <label className="row" style={{ gap: 6 }}>
                <input
                  type="checkbox"
                  checked={release.mandatory}
                  onChange={(e) => setRelease({ ...release, mandatory: e.target.checked })}
                />
                <span>필수 — 장비가 미루지 않습니다</span>
              </label>
              <button className="btn primary" disabled={busy === 'release'}>등록</button>
            </div>
          </form>
          <p className="muted" style={{ marginTop: 12, fontSize: 12 }}>
            체크섬 없이 깔면 <strong>중간에 잘린 파일도 설치됩니다</strong> — 장비에는 그것이
            그 파일인지 확인할 근거가 없습니다.
          </p>
        </div>
      )}
    </>
  )
}
