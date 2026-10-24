import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { reason, send, useAuth } from '../auth'
import { useTitle } from '../useTitle'

interface InviteInfo {
  role: 'ADMIN' | 'ARTIST'
  artistId: number | null
  artistName: string | null
  email: string
  needsPassword: boolean
}

const MIN_PASSWORD_LENGTH = 12

// Старонка са спасылкі ў лісьце-запрашэньні. Новы адрас задае тут пароль; ужо зарэгістраваны спачатку ўваходзіць.
export default function InvitePage() {
  const { t } = useTranslation()
  const { me } = useAuth()
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [info, setInfo] = useState<InviteInfo | 'failed' | null>(null)
  const [password, setPassword] = useState('')
  const [again, setAgain] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useTitle(t('invite.heading'))

  useEffect(() => {
    fetch(`/api/auth/invite?token=${encodeURIComponent(token)}`)
      .then((r) => (r.ok ? r.json() : 'failed'))
      .then(setInfo)
      .catch(() => setInfo('failed'))
  }, [token])

  async function accept(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (info !== null && info !== 'failed' && info.needsPassword && password !== again) {
      setError(t('account.mismatch'))
      return
    }
    const response = await send('POST', '/api/auth/invite', JSON.stringify({ token, password }), true)
    if (response.ok) {
      setDone(true)
    } else if (response.status === 410) {
      setInfo('failed')
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  if (info === null) {
    return <p className="muted">{t('common.loading')}</p>
  }
  if (info === 'failed') {
    return <p role="alert">{t('invite.failed')}</p>
  }
  const target = info.artistId == null ? '/' : `/artists/${info.artistId}`
  if (done) {
    return (
      <p>
        {info.role === 'ADMIN' ? t('invite.doneAdmin') : t('invite.done', { artist: info.artistName })}{' '}
        {me ? (
          // Новыя правы прыходзяць з сэрвэра, таму старонка загружаецца нанова.
          <a href={target}>{info.artistName ?? t('site.title')}</a>
        ) : (
          <Link to={`/login?next=${target}`}>{t('account.login')}</Link>
        )}
      </p>
    )
  }

  const loginFirst = !info.needsPassword && me?.email !== info.email
  return (
    <form className="admin-form" onSubmit={accept}>
      <h1>{t('invite.heading')}</h1>
      <p>
        {info.role === 'ADMIN'
          ? t('invite.textAdmin', { email: info.email })
          : t('invite.text', { artist: info.artistName, email: info.email })}
      </p>
      {loginFirst ? (
        <p>
          {t('invite.loginFirst', { email: info.email })}{' '}
          <Link to={`/login?next=${encodeURIComponent(`/invite?token=${token}`)}`}>{t('account.login')}</Link>
        </p>
      ) : (
        <>
          {info.needsPassword && (
            <>
              <p>{t('invite.setPassword')}</p>
              <label>
                {t('account.password')}
                <input
                  type="password"
                  autoComplete="new-password"
                  minLength={MIN_PASSWORD_LENGTH}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <span className="muted small">{t('account.passwordHint')}</span>
              </label>
              <label>
                {t('account.passwordAgain')}
                <input
                  type="password"
                  autoComplete="new-password"
                  minLength={MIN_PASSWORD_LENGTH}
                  required
                  value={again}
                  onChange={(e) => setAgain(e.target.value)}
                />
              </label>
            </>
          )}
          {error && <p role="alert">{error}</p>}
          <button type="submit">{t('invite.accept')}</button>
        </>
      )}
    </form>
  )
}
