import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { reason, send, useAuth } from '../auth'
import NotFound from './NotFound'
import { useTitle } from '../useTitle'

const MIN_PASSWORD_LENGTH = 12

export default function RegisterPage() {
  const { t } = useTranslation()
  const { registration } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [again, setAgain] = useState('')
  const [consent, setConsent] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useTitle(t('account.registerHeading'))

  if (!registration) {
    return <NotFound />
  }

  async function register(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (password !== again) {
      setError(t('account.mismatch'))
      return
    }
    const response = await send('POST', '/api/auth/register', JSON.stringify({ email, password }), true)
    if (response.ok) {
      setSent(true)
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  if (sent) {
    return <p>{t('account.sent')}</p>
  }

  return (
    <form className="admin-form" onSubmit={register}>
      <h1>{t('account.registerHeading')}</h1>
      <label>
        {t('account.email')}
        <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
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
      <label className="basis">
        <input type="checkbox" required checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>
          {t('account.consent')} <Link to="/about">{t('about.heading')}</Link>
        </span>
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">{t('account.register')}</button>
    </form>
  )
}
