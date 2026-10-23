import { useTranslation } from 'react-i18next'
import type { Loaded } from './api'

export default function Status({ loaded }: { loaded: Loaded<unknown> }) {
  const { t } = useTranslation()
  switch (loaded.state) {
    case 'loading':
      return <p className="muted">{t('common.loading')}</p>
    case 'notFound':
      return <p>{t('common.notFound')}</p>
    case 'error':
      return <p role="alert">{t('common.error')}</p>
    default:
      return null
  }
}
