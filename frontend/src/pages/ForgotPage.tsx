import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { reason, send } from '../auth'
import { useTitle } from '../useTitle'

// Просьба аб новым паролі. Адказ сэрвэра аднолькавы для любога адраса, таму і старонка кажа адно і тое ж.
export default function ForgotPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  useTitle(t('account.forgotHeading'))

  async function forgot(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const response = await send('POST', '/api/auth/forgot', JSON.stringify({ email }), true)
    if (response.ok) {
      setSent(true)
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  if (sent) {
    return <p>{t('account.resetSent')}</p>
  }

  return (
    <form className="admin-form" onSubmit={forgot}>
      <h1>{t('account.forgotHeading')}</h1>
      <p className="muted">{t('account.forgotHint')}</p>
      <label>
        {t('account.email')}
        <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">{t('account.forgotSend')}</button>
    </form>
  )
}
