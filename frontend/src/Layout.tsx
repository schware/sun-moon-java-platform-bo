import { NavLink, Outlet } from 'react-router-dom'
import { logout, type Me } from './lib/api'
import { GROUPS, GROUP_LABEL, MENU, canSeeScreen } from './lib/permissions'

interface Props {
  me: Me
  onSignedOut: () => void
}

/**
 * The shell every signed-in screen sits in: a fixed menu on the left,
 * content on the right.
 *
 * The menu only lists screens this operator has some permission on. That
 * is presentation, not enforcement — the server rejects the call either
 * way — but an admin console that shows doors nobody can open is a worse
 * console.
 *
 * 묶음 머리글도 같은 규칙을 따릅니다: 그 묶음에 보이는 화면이 하나도
 * 없으면 머리글도 안 그립니다. 빈 제목만 남으면 "여기 뭔가 있는데 내가
 * 못 보는 건가"를 묻게 되는데, 그건 이 화면이 대답할 수 있는 질문이
 * 아닙니다.
 */
export default function Layout({ me, onSignedOut }: Props) {
  const visible = MENU.filter((item) => canSeeScreen(me, item.screen))

  async function signOut() {
    try {
      await logout()
    } catch {
      // The session is going away from this browser regardless.
    }
    onSignedOut()
  }

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><span className="dot" /> sun-moon · BO</div>

        <nav>
          <NavLink to="/" end className={({ isActive }) => (isActive ? 'active' : '')}>
            홈
          </NavLink>
          {GROUPS.map((group) => {
            const items = visible.filter((item) => item.group === group)
            if (items.length === 0) return null
            return (
              <div className="nav-group" key={group}>
                <div className="nav-group-head">{GROUP_LABEL[group]}</div>
                {items.map((item) => (
                  <NavLink
                    key={item.screen}
                    to={item.path}
                    className={({ isActive }) => (isActive ? 'active' : '')}
                  >
                    {item.label}
                  </NavLink>
                ))}
              </div>
            )
          })}
        </nav>

        <div className="spacer" />

        <div className="who">
          <span className="name">{me.username}</span>
          {me.superAdmin && <span className="badge">전체 관리자</span>}
          <button className="btn" onClick={() => void signOut()}>로그아웃</button>
        </div>
      </aside>

      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
