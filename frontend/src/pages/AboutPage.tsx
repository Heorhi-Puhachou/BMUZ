import { useTranslation } from 'react-i18next'
import { useTitle } from '../useTitle'
import { MARK, withLink } from '../withLink'

export const EMAIL = 'admin@bmuz.eu'

export default function AboutPage() {
  const { t } = useTranslation()
  useTitle(t('about.heading'))
  const email = <a href={`mailto:${EMAIL}`}>{EMAIL}</a>

  return (
    <>
      <h1>{t('about.heading')}</h1>
      <p>{t('about.what')}</p>

      <h2>{t('about.contactHeading')}</h2>
      <p>{withLink(t('about.contact', { email: MARK }), email)}</p>
      <p>{t('about.contactReviews')}</p>
      <p>{t('about.contactOther')}</p>
    </>
  )
}
