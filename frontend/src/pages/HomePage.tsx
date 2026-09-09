import { Link } from 'react-router-dom'
import type { Me } from '../lib/api'
import { ACTIONS, MENU, can, canSeeScreen } from '../lib/permissions'

const ACTION_LABEL: Record<string, string> = {
  VIEW: '조회',
  CREATE: '신규',
  SAVE: '저장',
  DELETE: '삭제'
}

/**
 * Home is a permission summary rather than a dashboard of numbers. For an
 * operator the useful question on arrival is "what am I allowed to do
 * here", and until now that was only answerable by clicking things and
 * collecting 403s.
 */
export default function HomePage({ me }: { me: Me }) {
  const visible = MENU.filter((item) => canSeeScreen(me, item.screen))

  return (
    <>
      <div className="page-head">
        <h1>홈</h1>
        <span className="muted">{me.username}님으로 로그인되어 있습니다</span>
      </div>

      {me.superAdmin && (
        <div className="notice">
          전체 관리자 계정입니다 — 모든 화면의 모든 작업이 허용됩니다.
        </div>
      )}

      {visible.length === 0 ? (
        <div className="panel">
          <div className="empty">접근 가능한 화면이 없습니다. 관리자에게 권한을 요청하세요.</div>
        </div>
      ) : (
        <div className="panel">
          <h2>내 권한</h2>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>화면</th>
                  {ACTIONS.map((a) => <th key={a}>{ACTION_LABEL[a]}</th>)}
                </tr>
              </thead>
              <tbody>
                {visible.map((item) => (
                  <tr key={item.screen}>
                    <td><Link to={item.path}>{item.label}</Link></td>
                    {ACTIONS.map((a) => (
                      <td key={a}>
                        {can(me, item.screen, a)
                          ? <span className="badge on">허용</span>
                          : <span className="badge off">없음</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  )
}
