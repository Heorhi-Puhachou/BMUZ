import { type FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { reason, send, useAuth } from '../auth'
import { SPELLINGS } from '../i18n'
import { useChooseSpelling } from '../spelling'
import { useTitle } from '../useTitle'

// Свой уліковы запіс: профіль (імя і правапіс), зьмена адраса пошты і выдаленьне запісу. Адрас мяняецца толькі
// пасьля спасылкі з ліста на новы адрас.
export default function AccountPage() {
  const { t } = useTranslation()
  const { me } = useAuth()
  const [deleted, setDeleted] = useState(false)
  useTitle(t('account.heading'))
  if (deleted) {
    return <p role="status">{t('account.deleted')}</p>
  }
  if (!me) {
    return (
      <p>
        <Link to="/login?next=/account">{t('account.login')}</Link>
      </p>
    )
  }
  return (
    <>
      <h1>{t('account.heading')}</h1>
      <Profile />
      <EmailForm />
      {me.role !== 'SUPER_ADMIN' && <DeleteForm onDeleted={() => setDeleted(true)} />}
    </>
  )
}

function Profile() {
  const { t, i18n } = useTranslation()
  const { me, setMe } = useAuth()
  const chooseSpelling = useChooseSpelling()
  const [name, setName] = useState(me?.displayName ?? '')
  const [message, setMessage] = useState<string | null>(null)

  async function rename(event: FormEvent) {
    event.preventDefault()
    const response = await send('PUT', '/api/account/display-name', JSON.stringify({ displayName: name }), true)
    if (response.ok) {
      const saved = name.trim() || null
      setName(saved ?? '')
      if (me) {
        setMe({ ...me, displayName: saved })
      }
      setMessage(t('account.nameSaved'))
    } else if (response.status === 409) {
      setMessage(t('account.nameTaken'))
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <>
      <form className="admin-form" onSubmit={rename}>
        <h2>{t('account.profile')}</h2>
        <label>
          {t('account.displayName')}
          <input maxLength={40} value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <p className="muted small">{t('account.displayNameHint')}</p>
        <button type="submit">{t('account.nameSave')}</button>
      </form>
      {message && <p role="status">{message}</p>}
      <div className="admin-form">
        <fieldset>
          <legend>{t('account.spelling')}</legend>
          {SPELLINGS.map((s) => (
            <label key={s.tag} lang={s.tag}>
              <input
                type="radio"
                name="spelling"
                checked={i18n.language === s.tag}
                onChange={() => chooseSpelling(s.tag)}
              />
              {s.name}
            </label>
          ))}
        </fieldset>
        <p className="muted small">{t('account.spellingHint')}</p>
      </div>
    </>
  )
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

// Выдаленьне назаўжды: з паролем і адзнакай, што карыстальнік разумее, што вярнуць нельга.
function DeleteForm({ onDeleted }: { onDeleted: () => void }) {
  const { t } = useTranslation()
  const { setMe } = useAuth()
  const [password, setPassword] = useState('')
  const [understood, setUnderstood] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  async function remove(event: FormEvent) {
    event.preventDefault()
    const response = await send('POST', '/api/account/delete', JSON.stringify({ password }), true)
    if (response.ok) {
      onDeleted()
      setMe(null)
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
      <form className="admin-form" onSubmit={remove}>
        <h2>{t('account.deleteHeading')}</h2>
        <p className="muted small">{t('account.deleteHint')}</p>
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
        <fieldset>
          <label>
            <input type="checkbox" required checked={understood} onChange={(e) => setUnderstood(e.target.checked)} />
            {t('account.deleteUnderstood')}
          </label>
        </fieldset>
        <button type="submit">{t('account.deleteSend')}</button>
      </form>
      {message && <p role="status">{message}</p>}
    </>
  )
}
