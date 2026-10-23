import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useNavigate, useSearchParams } from 'react-router'

export default function Layout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [query, setQuery] = useState(params.get('q') ?? '')

  function search(event: FormEvent) {
    event.preventDefault()
    const q = query.trim()
    if (q) {
      navigate(`/search?q=${encodeURIComponent(q)}`)
    }
  }

  return (
    <>
      <header className="site-header">
        <div className="wrap">
          <Link to="/" className="site-title">{t('site.title')}</Link>
          <p className="tagline">{t('site.tagline')}</p>
          <form className="search" role="search" onSubmit={search}>
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={t('nav.searchPlaceholder')}
              aria-label={t('nav.searchPlaceholder')}
            />
            <button type="submit">{t('nav.search')}</button>
          </form>
        </div>
      </header>
      <main className="wrap">
        <Outlet />
      </main>
      <footer className="site-footer">
        <div className="wrap">
          <Link to="/about">{t('about.link')}</Link>
        </div>
      </footer>
    </>
  )
}
