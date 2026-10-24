import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { reason, send } from './auth'

interface Members {
  members: { userId: number; email: string; joinedAt: string }[]
  invites: { email: string; expiresAt: string }[]
}

// Панэль суперадміністратара: хто рэдагуе выканаўцу, і запрашэньне новага ўліковага запісу выканаўцы.
export default function ArtistMembers({ artistId }: { artistId: number }) {
  const { t } = useTranslation()
  const [data, setData] = useState<Members>({ members: [], invites: [] })
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    fetch(`/api/super/artists/${artistId}/members`)
      .then((r) => (r.ok ? r.json() : { members: [], invites: [] }))
      .then(setData)
  }, [artistId, version])

  async function act(response: Response, done: string) {
    setMessage(response.ok ? done : t('admin.failed', { reason: await reason(response) }))
    setVersion((v) => v + 1)
  }

  async function invite(event: FormEvent) {
    event.preventDefault()
    const response = await send('POST', `/api/super/artists/${artistId}/invites`, JSON.stringify({ email }), true)
    await act(response, t('members.invited', { email }))
    if (response.ok) {
      setEmail('')
    }
  }

  return (
    <section className="admin-panel">
      <h2>{t('members.heading')}</h2>
      {data.members.length === 0 && data.invites.length === 0 && <p className="muted">{t('members.none')}</p>}
      <ul className="plain">
        {data.members.map((m) => (
          <li key={m.userId}>
            {m.email} ·{' '}
            <button
              type="button"
              className="link-button"
              onClick={async () =>
                act(await send('DELETE', `/api/super/artists/${artistId}/members/${m.userId}`), t('edit.saved'))
              }
            >
              {t('members.remove')}
            </button>
          </li>
        ))}
        {data.invites.map((i) => (
          <li key={i.email} className="muted">
            {t('members.pending', { email: i.email, date: i.expiresAt.slice(0, 10) })} ·{' '}
            <button
              type="button"
              className="link-button"
              onClick={async () =>
                act(
                  await send('DELETE', `/api/super/artists/${artistId}/invites?email=${encodeURIComponent(i.email)}`),
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
          {t('members.inviteEmail')}
          <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <button type="submit">{t('members.invite')}</button>
      </form>
      {message && <p role="status">{message}</p>}
    </section>
  )
}
