import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { useApi } from '../api'
import { isAdmin, useAuth } from '../auth'
import NotFound from './NotFound'
import Status from '../Status'
import { useTitle } from '../useTitle'

interface Edit {
  id: number
  createdAt: string
  email: string
  action: string
  subject: string
  artistId: number | null
  albumId: number | null
  before: unknown
  after: unknown
}

// Сьпіс зьменаў каталёга для адміністратара: хто, калі, што, і як было да зьмены.
export default function EditsPage() {
  const { t } = useTranslation()
  const { me } = useAuth()
  useTitle(t('edits.heading'))
  if (!isAdmin(me)) {
    return <NotFound />
  }
  return <Edits />
}

function Edits() {
  const { t } = useTranslation()
  const loaded = useApi<Edit[]>('/api/admin/edits?size=200')
  if (loaded.state !== 'ok') {
    return <Status loaded={loaded} />
  }
  return (
    <>
      <h1>{t('edits.heading')}</h1>
      {loaded.data.length === 0 && <p className="muted">{t('edits.none')}</p>}
      <ul className="plain">
        {loaded.data.map((e) => (
          <li key={e.id} className="edit">
            <span className="muted">{e.createdAt.slice(0, 16).replace('T', ' ')}</span> · {e.email || t('edits.deletedAccount')} ·{' '}
            {t(`edits.action.${e.action}`)} ·{' '}
            {e.albumId != null ? (
              <Link to={`/albums/${e.albumId}`}>{e.subject}</Link>
            ) : e.artistId != null ? (
              <Link to={`/artists/${e.artistId}`}>{e.subject}</Link>
            ) : (
              e.subject
            )}
            <details>
              <summary>{t('edits.details')}</summary>
              <div className="edit-diff">
                <div>
                  <h3>{t('edits.before')}</h3>
                  <pre>{JSON.stringify(e.before, null, 2)}</pre>
                </div>
                <div>
                  <h3>{t('edits.after')}</h3>
                  <pre>{JSON.stringify(e.after, null, 2)}</pre>
                </div>
              </div>
            </details>
          </li>
        ))}
      </ul>
    </>
  )
}
