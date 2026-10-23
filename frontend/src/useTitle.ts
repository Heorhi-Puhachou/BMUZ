import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

export function useTitle(title: string | undefined) {
  const { t } = useTranslation()
  useEffect(() => {
    document.title = title ? `${title} — ${t('site.title')}` : t('site.title')
  }, [title, t])
}
