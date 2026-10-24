import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { reason, send, useAuth } from '../auth'
import NotFound from './NotFound'
import { useTitle } from '../useTitle'

interface Team {
  members: { userId: number; email: string; role: 'SUPER_ADMIN' | 'ADMIN' }[]
  invites: { email: string; expiresAt: string }[]
}

// Адміністратары сайта. Старонка суперадміністратара: толькі ён запрашае адміністратараў і забірае іхныя правы.
export default function TeamPage() {
  const { t } = useTranslation()
  const { me } = useAuth()
  useTitle(t('team.heading'))
  if (me?.role !== 'SUPER_ADMIN') {
    return <NotFound />
  }
  return <Admins />
}

function Admins() {
  const { t } = useTranslation()
  const [team, setTeam] = useState<Team>({ members: [], invites: [] })
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    fetch('/api/super/admins')
      .then((r) => (r.ok ? r.json() : { members: [], invites: [] }))
      .then(setTeam)
  }, [version])

  async function act(response: Response, done: string) {
    setMessage(response.ok ? done : t('admin.failed', { reason: await reason(response) }))
    setVersion((v) => v + 1)
  }

  async function invite(event: FormEvent) {
    event.preventDefault()
    const response = await send('POST', '/api/super/admins/invites', JSON.stringify({ email }), true)
    await act(response, t('members.invited', { email }))
    if (response.ok) {
      setEmail('')
    }
  }

  return (
    <>
      <h1>{t('team.heading')}</h1>
      <p className="muted">{t('team.hint')}</p>
      <ul className="plain">
        {team.members.map((m) => (
          <li key={m.userId}>
            {m.email}
            {m.role === 'SUPER_ADMIN' ? (
              ` (${t('account.superMark')})`
            ) : (
              <>
                {' · '}
                <button
                  type="button"
                  className="link-button"
                  onClick={async () => act(await send('DELETE', `/api/super/admins/${m.userId}`), t('edit.saved'))}
                >
                  {t('members.remove')}
                </button>
              </>
            )}
          </li>
        ))}
        {team.invites.map((i) => (
          <li key={i.email} className="muted">
            {t('members.pending', { email: i.email, date: i.expiresAt.slice(0, 10) })} ·{' '}
            <button
              type="button"
              className="link-button"
              onClick={async () =>
                act(
                  await send('DELETE', `/api/super/admins/invites?email=${encodeURIComponent(i.email)}`),
                  t('edit.saved'),
                )
              }
            >
              {t('members.cancel')}
            </button>
          </li>
        ))}
      </ul>
      <form className="admin-form" onSubmit={invite}>
        <label>
          {t('team.inviteEmail')}
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <button type="submit">{t('members.invite')}</button>
      </form>
      {message && <p role="status">{message}</p>}
    </>
  )
}
