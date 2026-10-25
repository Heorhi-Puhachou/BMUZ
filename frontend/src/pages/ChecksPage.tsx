import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { type CreditEntry, type Page, shortTime, useApi } from '../api'
import { isAdmin, useAuth } from '../auth'
import Credit from '../Credit'
import Pager from '../Pager'
import NotFound from './NotFound'
import Status from '../Status'
import { useTitle } from '../useTitle'

const PAGE_SIZE = 100

interface Item {
  id: number
  year: number
  title: string
  credit: CreditEntry[]
  updatedAt: string
  checkedAt: string | null
}

// Альбомы, якіх гэты адміністратар яшчэ не праглядзеў або якія зьмянілі пасьля ягонага прагляду.
export default function ChecksPage() {
  const { t } = useTranslation()
  const { me } = useAuth()
  useTitle(t('checks.heading'))
  if (!isAdmin(me)) {
    return <NotFound />
  }
  return <Checks />
}

function Checks() {
  const { t } = useTranslation()
  const [params] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const loaded = useApi<Page<Item>>(`/api/admin/albums?checked=false&page=${page}&size=${PAGE_SIZE}`)
  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  const { items, total } = loaded.data
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  return (
    <>
      <h1>{t('checks.heading')}</h1>
      <p className="muted">{t('checks.hint')}</p>
      {total === 0 && <p className="muted">{t('checks.none')}</p>}
      {pages > 1 && <Pager path="/checks" page={page} pages={pages} />}
      <ul className="plain">
        {items.map((album) => (
          <li key={album.id}>
            <Link to={`/albums/${album.id}`}>{album.title}</Link> · <Credit credit={album.credit} />
            <span className="muted"> · {album.year}</span>
            {album.checkedAt && (
              <span className="muted"> · {t('checks.changed', { date: shortTime(album.checkedAt) })}</span>
            )}
          </li>
        ))}
      </ul>
      {pages > 1 && <Pager path="/checks" page={page} pages={pages} />}
    </>
  )
}
