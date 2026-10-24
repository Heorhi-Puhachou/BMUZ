import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { reason, send } from '../auth'
import { useTitle } from '../useTitle'

const MIN_PASSWORD_LENGTH = 12

// Старонка са спасылкі ў лісьце: новы пароль. Спасылка аднаразовая і працуе гадзіну.
export default function ResetPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const [password, setPassword] = useState('')
  const [again, setAgain] = useState('')
  const [state, setState] = useState<'form' | 'done' | 'failed'>('form')
  const [error, setError] = useState<string | null>(null)
  useTitle(t('account.forgotHeading'))

  async function reset(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (password !== again) {
      setError(t('account.mismatch'))
      return
    }
    const token = params.get('token') ?? ''
    const response = await send('POST', '/api/auth/reset', JSON.stringify({ token, password }), true)
    if (response.ok) {
      setState('done')
    } else if (response.status === 410) {
      setState('failed')
    } else {
      setError(t('admin.failed', { reason: await reason(response) }))
    }
  }

  if (state === 'done') {
    return (
      <p>
        {t('account.resetDone')} <Link to="/login">{t('account.login')}</Link>
      </p>
    )
  }
  if (state === 'failed') {
    return (
      <p role="alert">
        {t('account.resetFailed')} <Link to="/forgot">{t('account.forgot')}</Link>
      </p>
    )
  }

  return (
    <form className="admin-form" onSubmit={reset}>
      <h1>{t('account.forgotHeading')}</h1>
      <label>
        {t('account.newPassword')}
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
      {error && <p role="alert">{error}</p>}
      <button type="submit">{t('account.resetSave')}</button>
    </form>
  )
}
