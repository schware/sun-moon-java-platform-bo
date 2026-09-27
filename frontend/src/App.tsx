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
import SoldOutPage from './pages/SoldOutPage'
import StoresPage from './pages/StoresPage'
import TablesPage from './pages/TablesPage'
import SalesPage from './pages/SalesPage'
import AppSalesPage from './pages/AppSalesPage'
import TerminalsPage from './pages/TerminalsPage'
import PosBusinessPage from './pages/PosBusinessPage'
import PosLivePage from './pages/PosLivePage'
import PosDeployPage from './pages/PosDeployPage'
import PosNoticePage from './pages/PosNoticePage'
import ReprocessPage from './pages/ReprocessPage'
import MasterPage from './pages/MasterPage'
import OperatorsPage from './pages/OperatorsPage'

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
        <Route path="/sold-out" element={<SoldOutPage me={me} />} />
        <Route path="/stores" element={<StoresPage me={me} />} />
        <Route path="/tables" element={<TablesPage me={me} />} />
        <Route path="/sales" element={<SalesPage me={me} />} />
        <Route path="/app-sales" element={<AppSalesPage me={me} />} />
        <Route path="/terminals" element={<TerminalsPage me={me} />} />
        <Route path="/pos-business" element={<PosBusinessPage me={me} />} />
        <Route path="/pos-live" element={<PosLivePage me={me} />} />
        <Route path="/pos-deploy" element={<PosDeployPage me={me} />} />
        <Route path="/pos-notices" element={<PosNoticePage me={me} />} />
        <Route path="/reprocess" element={<ReprocessPage me={me} />} />
        {/* 기준정보 전부가 이 경로 하나입니다 — 코드만 다릅니다. */}
        <Route path="/master/:code" element={<MasterPage me={me} />} />
        <Route path="/operators" element={<OperatorsPage me={me} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
