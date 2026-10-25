import { useTranslation } from 'react-i18next'
import type { Rating } from './api'

// Дзесяць квадратаў пад вокладкай. Без водгукаў — усе шэрыя. Інакш чырвоныя да сярэдняй: 8,5 — восем чырвоных,
// дзявяты напалову, дзясяты белы.
export default function RatingSquares({ rating }: { rating: Rating | null }) {
  const { t, i18n } = useTranslation()
  const label = rating
    ? t('rating.label', {
        average: rating.average.toLocaleString(i18n.language, { maximumFractionDigits: 1 }),
        count: rating.count,
      })
    : t('rating.none')
  return (
    <span className={rating ? 'rating' : 'rating none'} role="img" aria-label={label} title={label}>
      {Array.from({ length: 10 }, (_, i) => (
        <span
          key={i}
          style={{ '--fill': `${Math.min(1, Math.max(0, (rating?.average ?? 0) - i)) * 100}%` } as React.CSSProperties}
        />
      ))}
    </span>
  )
}
