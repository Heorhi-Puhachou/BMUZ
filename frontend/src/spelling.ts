import { useTranslation } from 'react-i18next'
import { send, useAuth } from './auth'

/**
 * Выбар правапісу. Наведнік памятае яго ў браўзэры; той, хто ўвайшоў, — яшчэ і ў профілі, каб правапіс вярнуўся
 * пры ўваходзе на любой прыладзе. Калі захаваць не ўдалося, выбар усё роўна дзейнічае да наступнага ўваходу.
 */
export function useChooseSpelling() {
  const { i18n } = useTranslation()
  const { me, setMe } = useAuth()
  return (tag: string) => {
    i18n.changeLanguage(tag)
    if (me && me.spelling !== tag) {
      setMe({ ...me, spelling: tag })
      send('PUT', '/api/account/spelling', JSON.stringify({ spelling: tag }), true).catch(() => {})
    }
  }
}
