import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { send, useAuth } from '../auth'
import { useTitle } from '../useTitle'

export default function LoginPage() {
  const { t } = useTranslation()
  const { me, setMe, registration } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  // Куды вярнуцца пасьля ўваходу — толькі адрас на гэтым сайце.
  const next = params.get('next')
  const target = next != null && next.startsWith('/') && !next.startsWith('//') ? next : '/'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  useTitle(t('account.loginHeading'))

  async function login(event: FormEvent) {
    event.preventDefault()
    setError(null)
    const response = await send('POST', '/api/auth/login', JSON.stringify({ email, password }), true)
    if (response.ok) {
      setMe(await response.json())
      navigate(target)
    } else if (response.status === 429) {
      setError(t('account.locked'))
    } else if (response.status === 403) {
      setError(t('account.notConfirmed'))
    } else {
      setError(t('account.wrong'))
    }
  }

  if (me) {
    return <p>{t('account.signedIn', { email: me.email })}</p>
  }

  return (
    <form className="admin-form" onSubmit={login}>
      <h1>{t('account.loginHeading')}</h1>
      <label>
        {t('account.email')}
        <input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
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
      {error && <p role="alert">{error}</p>}
      <button type="submit">{t('account.login')}</button>
      <Link to="/forgot">{t('account.forgot')}</Link>
      {registration && <Link to="/register">{t('account.register')}</Link>}
    </form>
  )
}
