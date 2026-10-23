import { useTranslation } from 'react-i18next'
import { useTitle } from '../useTitle'

const EMAIL = 'h.puhachou@gmail.com'

export default function AboutPage() {
  const { t } = useTranslation()
  useTitle(t('about.heading'))
  const email = <a href={`mailto:${EMAIL}`}>{EMAIL}</a>

  return (
    <>
      <h1>{t('about.heading')}</h1>
      <p>{t('about.what')}</p>

      <h2>{t('about.contactHeading')}</h2>
      <p>{withEmail(t('about.contact', { email: '\u0000' }), email)}</p>

      <h2>{t('about.privacyHeading')}</h2>
      <p>{t('about.privacy1')}</p>
      <p>{t('about.privacy2')}</p>
      <p>{withEmail(t('about.privacy3', { email: '\u0000' }), email)}</p>
    </>
  )
}

// Устаўляе спасылку на пошту туды, дзе ў перакладзе стаіць {{email}}.
function withEmail(text: string, email: React.ReactNode) {
  const [before, after] = text.split('\u0000')
  return (
    <>
      {before}
      {email}
      {after}
    </>
  )
}
