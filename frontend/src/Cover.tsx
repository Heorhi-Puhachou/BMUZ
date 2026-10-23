// Квадратная вокладка. Пакуль вокладкі няма, паказваецца заглушка: колер вылічаецца з назвы,
// таму адзін і той самы альбом заўсёды мае адзін і той самы колер.

function hue(text: string) {
  let hash = 0
  for (const char of text) {
    hash = (hash * 31 + char.codePointAt(0)!) % 360
  }
  return hash
}

function firstLetter(text: string) {
  const letter = [...text].find((char) => /\p{L}|\p{N}/u.test(char))
  return (letter ?? '·').toLocaleUpperCase('be')
}

export default function Cover({ url, title }: { url: string | null; title: string }) {
  if (url) {
    return <img className="cover" src={url} alt="" loading="lazy" />
  }
  return (
    <div className="cover stub" style={{ '--hue': hue(title) } as React.CSSProperties} aria-hidden="true">
      {firstLetter(title)}
    </div>
  )
}
