import { NavLink, Outlet } from 'react-router-dom'
import { logout, type Me } from './lib/api'
import { MENU, canSeeScreen } from './lib/permissions'

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
          {visible.map((item) => (
            <NavLink
              key={item.screen}
              to={item.path}
              className={({ isActive }) => (isActive ? 'active' : '')}
            >
              {item.label}
            </NavLink>
          ))}
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
