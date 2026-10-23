import Status from '../Status'
import { useTitle } from '../useTitle'

export default function NotFound() {
  useTitle(undefined)
  return <Status loaded={{ state: 'notFound' }} />
}
