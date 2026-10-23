import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useTitle } from '../useTitle'

const EMAIL = 'h.puhachou@gmail.com'
const SOURCE = 'https://github.com/Heorhi-Puhachou/BMUZ'
const MARK = '\u0000'

export default function AboutPage() {
  const { t } = useTranslation()
  useTitle(t('about.heading'))
  const email = <a href={`mailto:${EMAIL}`}>{EMAIL}</a>
  const source = <a href={SOURCE}>{SOURCE.replace('https://', '')}</a>

  return (
    <>
      <h1>{t('about.heading')}</h1>
      <p>{t('about.what')}</p>

      <h2>{t('about.contactHeading')}</h2>
      <p>{withLink(t('about.contact', { email: MARK }), email)}</p>

      <h2>{t('about.sourceHeading')}</h2>
      <p>{withLink(t('about.source', { link: MARK }), source)}</p>

      <h2>{t('about.privacyHeading')}</h2>
      <p>{t('about.privacy1')}</p>
      <p>{t('about.privacy2')}</p>
      <p>{withLink(t('about.privacy3', { email: MARK }), email)}</p>
    </>
  )
}

// Устаўляе спасылку туды, дзе ў перакладзе стаіць {{email}} або {{link}}.
function withLink(text: string, link: ReactNode) {
  const [before, after] = text.split(MARK)
  return (
    <>
      {before}
      {link}
      {after}
    </>
  )
}
