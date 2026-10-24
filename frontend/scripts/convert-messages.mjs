// Робіць afi.json (афіцыйны правапіс) і lac.json (łacinka) з tar.json праз orthography-converter.
// Пасьля канвэртацыі накладае ручныя выключэньні з overrides.json. Кожнае выключэньне памятае тэкст тарашкевіцы,
// для якога зроблена, і калі той тэкст зьмяніўся, скрыпт спыняецца: выключэньне трэба перарабіць.
// Потым правярае вынік, перш чым запісаць:
// тыя самыя ключы, тыя самыя {{пэрамэтры}}, лацінскія словы зыходніка без зьменаў і ніякага новага «null».
//
// Ужываньне: npm run messages
// Шлях да jar-файла — ORTHOGRAPHY_CONVERTER_JAR або ../orthography-converter побач з рэпазыторыем BMUZ.

import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const messages = resolve(here, '../src/messages')
const source = join(messages, 'tar.json')
const jar =
  process.env.ORTHOGRAPHY_CONVERTER_JAR ??
  resolve(here, '../../../orthography-converter/target/orthography-converter.jar')

const targets = [
  { tag: 'be', file: 'afi.json', orthography: 'KA' },
  { tag: 'be-Latn', file: 'lac.json', orthography: 'ŁT' },
]

function flatten(object, prefix = '') {
  return Object.entries(object).flatMap(([key, value]) =>
    typeof value === 'object' ? flatten(value, `${prefix}${key}.`) : [[`${prefix}${key}`, value]],
  )
}

function setPath(object, path, value) {
  const keys = path.split('.')
  let node = object
  for (const key of keys.slice(0, -1)) {
    node = node[key]
  }
  if (!(keys.at(-1) in node)) {
    throw new Error(`override for a missing key: ${path}`)
  }
  node[keys.at(-1)] = value
}

const placeholders = (text) => (text.match(/\{\{\w+\}\}/g) ?? []).sort().join(' ')
const latinWords = (text) => text.match(/[\p{Script=Latin}]+(?:[.@-][\p{Script=Latin}]+)*/gu) ?? []

const sourceEntries = new Map(flatten(JSON.parse(readFileSync(source, 'utf8'))))
const overrides = JSON.parse(readFileSync(join(messages, 'overrides.json'), 'utf8'))
const work = mkdtempSync(join(tmpdir(), 'bmuz-messages-'))
const problems = []

try {
  for (const { tag, file, orthography } of targets) {
    const out = join(work, file)
    execFileSync('java', ['-jar', jar, 'KK', source, orthography, out], { stdio: 'inherit' })
    const converted = JSON.parse(readFileSync(out, 'utf8'))
    for (const [path, override] of Object.entries(overrides[tag] ?? {})) {
      if (sourceEntries.get(path) !== override.source) {
        problems.push(`${tag} ${path}: tar.json changed since this override was written; redo it`)
        continue
      }
      setPath(converted, path, override.text)
    }

    const entries = new Map(flatten(converted))
    if ([...entries.keys()].join() !== [...sourceEntries.keys()].join()) {
      problems.push(`${tag}: keys differ from tar.json`)
    }
    for (const [key, original] of sourceEntries) {
      const text = entries.get(key) ?? ''
      if (placeholders(text) !== placeholders(original)) {
        problems.push(`${tag} ${key}: placeholders changed: ${text}`)
      }
      for (const word of latinWords(original)) {
        if (!text.includes(word)) {
          problems.push(`${tag} ${key}: Latin word "${word}" lost: ${text}`)
        }
      }
      if (/null/i.test(text) && !/null/i.test(original)) {
        problems.push(`${tag} ${key}: "null" appeared: ${text}`)
      }
    }
    if (problems.length === 0) {
      writeFileSync(join(messages, file), JSON.stringify(converted, null, 2) + '\n')
      console.log(`wrote src/messages/${file}`)
    }
  }
} finally {
  rmSync(work, { recursive: true, force: true })
}

if (problems.length > 0) {
  console.error(problems.join('\n'))
  process.exit(1)
}
