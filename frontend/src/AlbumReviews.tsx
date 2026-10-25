import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { type Album, type Rating, shortTime, useApi } from './api'
import { canEdit, isAdmin, reason, send, useAuth } from './auth'
import RatingSquares from './RatingSquares'

interface Review {
  id: number
  author: string
  score: number
  text: string
  createdAt: string
  updatedAt: string
  mine: boolean
}

const MIN_TEXT = 10
const HOUR = 60 * 60 * 1000

// Водгукі пад альбомам. Ацэнка ад 1 да 10 толькі разам з тэкстам, і пад кожным водгукам — імя аўтара.
export default function AlbumReviews({ album }: { album: Album }) {
  const { t, i18n } = useTranslation()
  const { me, registration } = useAuth()
  const [version, setVersion] = useState(0)
  const [now] = useState(() => Date.now())
  const loaded = useApi<{ rating: Rating | null; reviews: Review[] }>(`/api/albums/${album.id}/reviews`, version)
  if (loaded.state !== 'ok') {
    return null
  }
  const { rating, reviews } = loaded.data
  const mine = reviews.find((r) => r.mine)
  const own = !isAdmin(me) && canEdit(me, album.credit.map((c) => c.artistId))
  const from = me?.reviewsFrom ? new Date(me.reviewsFrom) : null
  const waiting = from != null && from.getTime() > now

  async function remove(id: number) {
    const response = await send('DELETE', `/api/admin/reviews/${id}`)
    if (response.ok) {
      setVersion((v) => v + 1)
    }
  }

  return (
    <section>
      <h2>{t('reviews.heading')}</h2>
      <div className="basis">
        <RatingSquares rating={rating} />
        <span className="muted small">
          {rating
            ? t('reviews.summary', {
                average: rating.average.toLocaleString(i18n.language, { maximumFractionDigits: 1 }),
                count: rating.count,
              })
            : t('rating.none')}
        </span>
      </div>

      {!me ? (
        <p className="banner">
          {t('reviews.visitorBanner')}{' '}
          {registration && (
            <>
              <Link to="/register">{t('account.register')}</Link>
              {' · '}
            </>
          )}
          <Link to={`/login?next=/albums/${album.id}`}>{t('reviews.loginToReview')}</Link>
        </p>
      ) : own ? (
        <p className="muted">{t('reviews.ownAlbum')}</p>
      ) : waiting ? (
        <p className="banner">
          {t('reviews.waitBanner', {
            time: from.toLocaleString(i18n.language, {
              year: 'numeric',
              month: '2-digit',
              day: '2-digit',
              hour: '2-digit',
              minute: '2-digit',
            }),
            hours: Math.ceil((from.getTime() - now) / HOUR),
          })}
        </p>
      ) : !me.displayName ? (
        <p>
          <Link to="/account">{t('reviews.needName')}</Link>
        </p>
      ) : (
        <ReviewForm
          key={mine?.updatedAt ?? 'new'}
          albumId={album.id}
          mine={mine}
          onChange={() => setVersion((v) => v + 1)}
        />
      )}

      <ul className="plain">
        {reviews.map((r) => (
          <li key={r.id} className="review">
            <strong>{r.author}</strong> · {t('reviews.score', { score: r.score })}
            <span className="muted"> · {shortTime(r.createdAt)}</span>
            {isAdmin(me) && !r.mine && (
              <>
                {' · '}
                <button type="button" className="link-button" onClick={() => remove(r.id)}>
                  {t('reviews.remove')}
                </button>
              </>
            )}
            <p>{r.text}</p>
          </li>
        ))}
      </ul>
    </section>
  )
}

function ReviewForm({ albumId, mine, onChange }: { albumId: number; mine?: Review; onChange: () => void }) {
  const { t } = useTranslation()
  const [score, setScore] = useState<number | null>(mine?.score ?? null)
  const [text, setText] = useState(mine?.text ?? '')
  const [message, setMessage] = useState<string | null>(null)
  const length = [...text.trim()].length

  async function save(event: FormEvent) {
    event.preventDefault()
    if (score == null) {
      setMessage(t('reviews.pickScore'))
      return
    }
    const response = await send('PUT', `/api/account/reviews/${albumId}`, JSON.stringify({ score, text }), true)
    if (response.ok) {
      setMessage(null)
      onChange()
    } else if (response.status === 429) {
      setMessage(t('reviews.tooMany'))
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  async function remove() {
    const response = await send('DELETE', `/api/account/reviews/${albumId}`)
    if (response.ok) {
      onChange()
    } else {
      setMessage(t('admin.failed', { reason: await reason(response) }))
    }
  }

  return (
    <form className="admin-form" onSubmit={save}>
      <fieldset>
        <legend>{t('reviews.yourScore')}</legend>
        <div className="score-picker">
          {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
            <button key={n} type="button" aria-pressed={score === n} onClick={() => setScore(n)}>
              {n}
            </button>
          ))}
        </div>
      </fieldset>
      <label>
        {t('reviews.text')}
        <textarea rows={4} maxLength={2000} required value={text} onChange={(e) => setText(e.target.value)} />
      </label>
      <p className="muted small">{t('reviews.textHint', { count: length, min: MIN_TEXT })}</p>
      <div>
        <button type="submit" disabled={length < MIN_TEXT}>
          {t(mine ? 'reviews.update' : 'reviews.save')}
        </button>
        {mine && (
          <>
            {' '}
            <button type="button" onClick={remove}>
              {t('reviews.delete')}
            </button>
          </>
        )}
      </div>
      {message && <p role="status">{message}</p>}
    </form>
  )
}
