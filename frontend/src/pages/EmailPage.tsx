import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { send, useAuth } from '../auth'
import { useTitle } from '../useTitle'

// Старонка са спасылкі ў лісьце на новы адрас. Токен пасылаецца адзін раз: спасылка аднаразовая. Пасьля зьмены ўсе
// сэсіі запісу заканчваюцца, таму старонка выходзіць і тут.
export default function EmailPage() {
  const { t } = useTranslation()
  const { setMe } = useAuth()
  const [params] = useSearchParams()
  const [state, setState] = useState<'working' | 'done' | 'taken' | 'failed'>('working')
  const sent = useRef(false)
  useTitle(t('account.changeEmail'))

  useEffect(() => {
    if (sent.current) {
      return
    }
    sent.current = true
    send('POST', '/api/auth/email', JSON.stringify({ token: params.get('token') ?? '' }), true)
      .then((r) => {
        if (r.ok) {
          setMe(null)
        }
        setState(r.ok ? 'done' : r.status === 409 ? 'taken' : 'failed')
      })
      .catch(() => setState('failed'))
  }, [params, setMe])

  if (state === 'working') {
    return <p className="muted">{t('account.confirming')}</p>
  }
  if (state === 'taken') {
    return <p role="alert">{t('account.emailTaken')}</p>
  }
  if (state === 'failed') {
    return <p role="alert">{t('account.emailFailed')}</p>
  }
  return (
    <p>
      {t('account.emailChanged')} <Link to="/login">{t('account.login')}</Link>
    </p>
  )
}
