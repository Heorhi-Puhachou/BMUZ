import { useTranslation } from 'react-i18next'
import { useAuth } from '../auth'
import { useTitle } from '../useTitle'
import { MARK, withLink } from '../withLink'
import { EMAIL } from './AboutPage'

// Апавяшчэньне паводле арт. 13 GDPR. Пра водгукі — толькі калі яны ўключаныя на сайце.
export default function PrivacyPage() {
  const { t } = useTranslation()
  const { reviews } = useAuth()
  useTitle(t('privacy.heading'))
  const email = <a href={`mailto:${EMAIL}`}>{EMAIL}</a>

  return (
    <>
      <h1>{t('privacy.heading')}</h1>
      <p>{withLink(t('privacy.controller', { email: MARK }), email)}</p>

      <h2>{t('privacy.visitorsHeading')}</h2>
      <p>{t('privacy.visitors1')}</p>
      <p>{t('privacy.visitors2')}</p>

      <h2>{t('privacy.accountHeading')}</h2>
      <p>{t('privacy.account1')}</p>
      <p>{t('privacy.account2')}</p>
      {reviews && <p>{t('privacy.reviews')}</p>}

      <h2>{t('privacy.editorsHeading')}</h2>
      <p>{t('privacy.editors')}</p>

      <h2>{t('privacy.deleteHeading')}</h2>
      <p>{t('privacy.delete')}</p>

      <h2>{t('privacy.rightsHeading')}</h2>
      <p>{t('privacy.where')}</p>
      <p>{t('privacy.rights')}</p>
    </>
  )
}
