import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import afi from './messages/afi.json'
import lac from './messages/lac.json'
import tar from './messages/tar.json'

// Тарашкевіца — асноўны правапіс; астатнія два робіць з яе `npm run messages`.
// Назва кожнага варыянта напісаная ім самім, таму яе не перакладаюць.
// tag — міжнародны код мовы для <html lang>; файлы названыя коратка, а коды tar, afi, lac занятыя іншымі мовамі.
export const SPELLINGS = [
  { tag: 'be-tarask', label: 'ТАР', name: 'Тарашкевіца' },
  { tag: 'be', label: 'АФІ', name: 'Афіцыйны правапіс' },
  { tag: 'be-Latn', label: 'ŁAC', name: 'Łacinka' },
] as const

const STORAGE_KEY = 'bmuz.spelling'
const DEFAULT = 'be-tarask'

function savedSpelling() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    return SPELLINGS.some((s) => s.tag === saved) ? saved! : DEFAULT
  } catch {
    return DEFAULT
  }
}

i18n.use(initReactI18next).init({
  resources: {
    'be-tarask': { translation: tar },
    be: { translation: afi },
    'be-Latn': { translation: lac },
  },
  lng: savedSpelling(),
  fallbackLng: DEFAULT,
  interpolation: { escapeValue: false },
})

document.documentElement.lang = i18n.language

i18n.on('languageChanged', (tag) => {
  document.documentElement.lang = tag
  try {
    localStorage.setItem(STORAGE_KEY, tag)
  } catch {
    // Браўзэр не дазваляе захоўваць: выбар проста не запомніцца.
  }
})

export default i18n
