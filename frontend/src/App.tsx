import { useCallback, useEffect, useState } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { fetchMe, UnauthorizedError, type Me } from './lib/api'
import Layout from './Layout'
import LoginPage from './pages/LoginPage'
import HomePage from './pages/HomePage'
import BoardPage from './pages/BoardPage'
import CommonCodePage from './pages/CommonCodePage'
import DevicesPage from './pages/DevicesPage'
import MenuPage from './pages/MenuPage'
import StoresPage from './pages/StoresPage'

type Session = { state: 'loading' } | { state: 'out' } | { state: 'in'; me: Me }

export default function App() {
  const [session, setSession] = useState<Session>({ state: 'loading' })

  const refresh = useCallback(async () => {
    try {
      setSession({ state: 'in', me: await fetchMe() })
    } catch (e) {
      // Anything other than "not logged in" is still a reason to show the
      // login screen — there is nothing else a visitor can do from here.
      if (!(e instanceof UnauthorizedError)) console.error(e)
      setSession({ state: 'out' })
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  if (session.state === 'loading') {
    return <div className="login-wrap"><p className="muted">불러오는 중…</p></div>
  }

  if (session.state === 'out') {
    return (
      <Routes>
        <Route path="*" element={<LoginPage onSignedIn={refresh} />} />
      </Routes>
    )
  }

  const me = session.me
  return (
    <Routes>
      <Route element={<Layout me={me} onSignedOut={() => setSession({ state: 'out' })} />}>
        <Route path="/" element={<HomePage me={me} />} />
        <Route path="/board" element={<BoardPage me={me} />} />
        <Route path="/common-code" element={<CommonCodePage me={me} />} />
        <Route path="/devices" element={<DevicesPage me={me} />} />
        <Route path="/menus" element={<MenuPage me={me} />} />
        <Route path="/stores" element={<StoresPage me={me} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
