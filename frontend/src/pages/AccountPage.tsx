import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { reason, send, useAuth } from '../auth'
import { useTitle } from '../useTitle'

// Свой уліковы запіс: зьмена адраса пошты. Адрас мяняецца толькі пасьля спасылкі з ліста на новы адрас.
export default function AccountPage() {
  const { t } = useTranslation()
  const { me } = useAuth()
  useTitle(t('account.heading'))
  if (!me) {
    return (
      <p>
        <Link to="/login?next=/account">{t('account.login')}</Link>
      </p>
    )
  }
  return <EmailForm />
}

function EmailForm() {
  const { t } = useTranslation()
  const [current, setCurrent] = useState<{ email: string; pending: string | null } | null>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [version, setVersion] = useState(0)

  useEffect(() => {
    fetch('/api/account/email')
      .then((r) => (r.ok ? r.json() : null))
      .then(setCurrent)
  }, [version])

  async function change(event: FormEvent) {
    event.preventDefault()
    const response = await send('POST', '/api/account/email', JSON.stringify({ password, email }), true)
    if (response.ok) {
      setMessage(t('account.emailSent', { email: email.trim() }))
      setEmail('')
      setPassword('')
      setVersion((v) => v + 1)
    } else if (response.status === 403) {
      setMessage(t('account.wrongPassword'))
    } else if (response.status === 429) {
      setMessage(t('account.locked'))
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <>
      <h1>{t('account.heading')}</h1>
      {current && <p>{t('account.currentEmail', { email: current.email })}</p>}
      {current?.pending && <p className="muted">{t('account.pendingEmail', { email: current.pending })}</p>}
      <form className="admin-form" onSubmit={change}>
        <h2>{t('account.changeEmail')}</h2>
        <p className="muted small">{t('account.changeEmailHint')}</p>
        <label>
          {t('account.newEmail')}
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          {t('account.password')}
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit">{t('account.changeEmailSend')}</button>
      </form>
      {message && <p role="status">{message}</p>}
    </>
  )
}
