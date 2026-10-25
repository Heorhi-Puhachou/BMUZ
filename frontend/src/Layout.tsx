import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useNavigate, useSearchParams } from 'react-router'
import { isAdmin, send, useAuth } from './auth'
import { SPELLINGS } from './i18n'
import { useListFilter } from './listFilter'
import { useChooseSpelling } from './spelling'

export default function Layout() {
  const { t, i18n } = useTranslation()
  const filter = useListFilter()
  const { me, setMe, registration } = useAuth()
  const navigate = useNavigate()
  const chooseSpelling = useChooseSpelling()

  async function logout() {
    await send('POST', '/api/auth/logout')
    setMe(null)
  }
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
          <div className="topbar">
            <nav className="spellings" aria-label="Правапіс · Pravapis">
              {SPELLINGS.map((s) => (
                <button
                  key={s.tag}
                  type="button"
                  lang={s.tag}
                  title={s.name}
                  aria-label={s.name}
                  aria-pressed={i18n.language === s.tag}
                  onClick={() => chooseSpelling(s.tag)}
                >
                  {s.label}
                </button>
              ))}
            </nav>
            <div className="topbar-right">
              {me ? (
                <span className="account" title={t('account.signedIn', { email: me.email })}>
                  <Link to="/account">{me.email}</Link>
                  {me.role === 'SUPER_ADMIN' && ` (${t('account.superMark')})`}
                  {me.role === 'ADMIN' && ` (${t('account.adminMark')})`}
                  {me.role === 'ARTIST' && ` (${t('account.artistMark')})`} ·{' '}
                  {me.role === 'SUPER_ADMIN' && (
                    <>
                      <Link to="/team">{t('team.heading')}</Link> ·{' '}
                    </>
                  )}
                  {isAdmin(me) && (
                    <>
                      <Link to="/checks">{t('checks.heading')}</Link> ·{' '}
                      <Link to="/edits">{t('edits.heading')}</Link> ·{' '}
                    </>
                  )}
                  <button type="button" className="link-button" onClick={logout}>
                    {t('account.logout')}
                  </button>
                </span>
              ) : (
                <span className="account">
                  <Link to="/login">{t('account.login')}</Link>
                  {registration && (
                    <>
                      {' · '}
                      <Link to="/register">{t('account.register')}</Link>
                    </>
                  )}
                </span>
              )}
            </div>
          </div>
          <Link to="/" className="site-title">{t('site.title')}</Link>
          <p className="tagline">{t('site.tagline')}</p>
          <label className="free-only">
            <input type="checkbox" checked={filter.on} onChange={(e) => filter.setOn(e.target.checked)} />
            {t(filter.kind === 'unchecked' ? 'filter.unchecked' : 'filter.freeOnly')}
          </label>
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
