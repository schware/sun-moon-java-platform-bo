import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { apiGet, apiWrite, type Me, type Post } from '../lib/api'
import { can } from '../lib/permissions'

type Editing = { mode: 'none' } | { mode: 'new' } | { mode: 'edit'; post: Post }

export default function BoardPage({ me }: { me: Me }) {
  const [posts, setPosts] = useState<Post[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState<Editing>({ mode: 'none' })
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(false)

  const mayView = can(me, 'BOARD', 'VIEW')
  const mayCreate = can(me, 'BOARD', 'CREATE')
  const maySave = can(me, 'BOARD', 'SAVE')
  const mayDelete = can(me, 'BOARD', 'DELETE')

  const load = useCallback(async () => {
    if (!mayView) {
      setLoading(false)
      return
    }
    setLoading(true)
    try {
      setPosts(await apiGet<Post[]>('/board'))
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다')
    } finally {
      setLoading(false)
    }
  }, [mayView])

  useEffect(() => {
    void load()
  }, [load])

  function startNew() {
    setTitle('')
    setContent('')
    setEditing({ mode: 'new' })
  }

  function startEdit(post: Post) {
    setTitle(post.title)
    setContent(post.content)
    setEditing({ mode: 'edit', post })
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      // create and save are separate calls, never an upsert — 신규 and
      // 저장 are separate permissions on the server.
      if (editing.mode === 'new') {
        await apiWrite('POST', '/board', { title, content })
      } else if (editing.mode === 'edit') {
        await apiWrite('PUT', `/board/${editing.post.id}`, { title, content })
      }
      setEditing({ mode: 'none' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '저장하지 못했습니다')
    } finally {
      setBusy(false)
    }
  }

  async function remove(post: Post) {
    if (!window.confirm(`"${post.title}" 을(를) 삭제할까요?`)) return
    setError(null)
    try {
      await apiWrite('DELETE', `/board/${post.id}`)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했습니다')
    }
  }

  if (!mayView) {
    return (
      <>
        <div className="page-head"><h1>공지사항</h1></div>
        <div className="panel"><div className="empty">이 화면의 조회 권한이 없습니다.</div></div>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <h1>공지사항</h1>
        <button className="btn primary" onClick={startNew} disabled={!mayCreate}>
          새 글
        </button>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {editing.mode !== 'none' && (
        <form className="panel" onSubmit={(e) => void submit(e)}>
          <h2>{editing.mode === 'new' ? '새 글' : '글 수정'}</h2>
          <div className="field">
            <label htmlFor="title">제목</label>
            <input id="title" type="text" maxLength={200} value={title}
                   onChange={(e) => setTitle(e.target.value)} required autoFocus />
          </div>
          <div className="field">
            <label htmlFor="content">내용</label>
            <textarea id="content" maxLength={10000} value={content}
                      onChange={(e) => setContent(e.target.value)} required />
          </div>
          <div className="row">
            <button className="btn primary" type="submit" disabled={busy || !title.trim() || !content.trim()}>
              {busy ? '저장 중…' : '저장'}
            </button>
            <button className="btn" type="button" onClick={() => setEditing({ mode: 'none' })}>취소</button>
          </div>
        </form>
      )}

      <div className="panel">
        {loading ? (
          <div className="empty">불러오는 중…</div>
        ) : posts.length === 0 ? (
          <div className="empty">등록된 공지가 없습니다.</div>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style={{ width: '64px' }}>번호</th>
                  <th>제목</th>
                  <th style={{ width: '120px' }}>작성자</th>
                  <th style={{ width: '150px' }}>작성일</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {posts.map((post) => (
                  <tr key={post.id}>
                    <td className="muted">{post.id}</td>
                    <td>
                      <div>{post.title}</div>
                      <div className="muted" style={{ whiteSpace: 'pre-wrap', marginTop: '4px' }}>
                        {post.content}
                      </div>
                    </td>
                    <td className="muted">{post.author}</td>
                    <td className="muted">{formatDate(post.createdAt)}</td>
                    <td className="actions">
                      <div className="row" style={{ justifyContent: 'flex-end' }}>
                        <button className="btn" onClick={() => startEdit(post)} disabled={!maySave}>수정</button>
                        <button className="btn danger" onClick={() => void remove(post)} disabled={!mayDelete}>삭제</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  )
}

/** The API sends an ISO instant; operators read local time. */
function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })
}
