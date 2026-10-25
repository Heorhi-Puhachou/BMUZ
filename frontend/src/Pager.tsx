import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

/**
 * Старонкі сьпісу: стрэлкі і «2/4» пасярэдзіне. Лічыльнік стаіць у цэнтры, нават калі адной стрэлкі няма.
 * `onNavigate` атрымлівае гэты радок, калі на ім націснулі стрэлку, — каб пасьля застаўся на тым самым месцы.
 */
export default function Pager({
  path,
  page,
  pages,
  onNavigate,
}: {
  path: string
  page: number
  pages: number
  onNavigate?: (pager: HTMLElement) => void
}) {
  const { t } = useTranslation()
  const nav = useRef<HTMLElement>(null)
  const clicked = () => {
    if (nav.current) {
      onNavigate?.(nav.current)
    }
  }
  return (
    <nav className="pager" aria-label={t('pager.label')} ref={nav}>
      <span>
        {page > 0 && (
          <Link to={`${path}?page=${page - 1}`} aria-label={t('pager.prevLabel')} onClick={clicked}>
            {t('pager.prev')}
          </Link>
        )}
      </span>
      <span className="muted">{t('pager.page', { page: page + 1, pages })}</span>
      <span>
        {page + 1 < pages && (
          <Link to={`${path}?page=${page + 1}`} aria-label={t('pager.nextLabel')} onClick={clicked}>
            {t('pager.next')}
          </Link>
        )}
      </span>
    </nav>
  )
}
