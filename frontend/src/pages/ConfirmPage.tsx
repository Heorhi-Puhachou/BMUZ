import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { send } from '../auth'
import { useTitle } from '../useTitle'

// Старонка са спасылкі ў лісьце. Токен пасылаецца адзін раз: спасылка аднаразовая.
export default function ConfirmPage() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const [state, setState] = useState<'working' | 'done' | 'failed'>('working')
  const sent = useRef(false)
  useTitle(t('account.registerHeading'))

  useEffect(() => {
    if (sent.current) {
      return
    }
    sent.current = true
    send('POST', '/api/auth/confirm', JSON.stringify({ token: params.get('token') ?? '' }), true)
      .then((r) => setState(r.ok ? 'done' : 'failed'))
      .catch(() => setState('failed'))
  }, [params])

  if (state === 'working') {
    return <p className="muted">{t('account.confirming')}</p>
  }
  if (state === 'failed') {
    return <p role="alert">{t('account.confirmFailed')}</p>
  }
  return (
    <p>
      {t('account.confirmed')} <Link to="/login">{t('account.login')}</Link>
    </p>
  )
}
