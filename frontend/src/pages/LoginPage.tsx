import { useState, type FormEvent } from 'react'
import { login } from '../lib/api'

interface Props {
  onSignedIn: () => void | Promise<void>
  /** 쓰던 중에 로그인이 끊겨서 여기로 왔는가. */
  expired?: boolean
}

export default function LoginPage({ onSignedIn, expired }: Props) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      await login(username, password)
      await onSignedIn()
    } catch (e) {
      // The server answers a bad password with 401 and a message; show
      // that rather than inventing one, but never say which half was wrong.
      setError(e instanceof Error ? e.message : '로그인에 실패했습니다')
      setPassword('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="panel login-card" onSubmit={(e) => void submit(e)}>
        <h1>Back Office</h1>
        <p className="muted sub">운영자 계정으로 로그인하세요.</p>

        {expired && !error && (
          <div className="notice" role="status">
            로그인이 끊겼습니다. 다시 로그인하시면 보던 화면으로 돌아갑니다.
          </div>
        )}
        {error && <div className="error" role="alert">{error}</div>}

        <div className="field">
          <label htmlFor="username">아이디</label>
          <input
            id="username"
            type="text"
            autoComplete="username"
            autoFocus
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <button className="btn primary wide" type="submit" disabled={busy || !username || !password}>
          {busy ? '확인 중…' : '로그인'}
        </button>
      </form>
    </div>
  )
}
