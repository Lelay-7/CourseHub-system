import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { THEMES, THEME_STORAGE_KEY, isTheme, readTheme, applyTheme } from '../src/utils/theme.js'

const originalDocument = globalThis.document
const originalStorage = globalThis.localStorage
afterEach(() => {
  if (originalDocument === undefined) delete globalThis.document
  else globalThis.document = originalDocument
  if (originalStorage === undefined) delete globalThis.localStorage
  else globalThis.localStorage = originalStorage
})

function fixture(saved, storageBlocked = false) {
  const values = new Map([['access_token', 'existing-session'], ['refresh_token', 'existing-refresh']])
  if (saved !== undefined) values.set(THEME_STORAGE_KEY, saved)
  const classes = new Set()
  const root = { dataset: {}, style: {}, classList: { toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name) } } }
  const storage = {
    getItem(key) { if (storageBlocked) throw new Error('storage blocked'); return values.get(key) ?? null },
    setItem(key, value) { if (storageBlocked) throw new Error('storage blocked'); values.set(key, String(value)) }
  }
  globalThis.document = { documentElement: root }
  globalThis.localStorage = storage
  return { root, classes, values, storage }
}

test('missing and invalid stored themes fall back to the original blue palette', () => {
  for (const saved of [undefined, '', 'removed-theme', '__proto__']) {
    fixture(saved)
    assert.equal(readTheme(), 'blue')
    assert.equal(applyTheme(saved), 'blue')
    assert.equal(document.documentElement.dataset.theme, 'blue')
    assert.equal(document.documentElement.style.colorScheme, 'light')
  }
})

test('all six palettes persist and restore without changing the signed-in session', () => {
  const { values } = fixture()
  assert.equal(THEMES.length, 6)
  assert.equal(new Set(THEMES.map(theme => theme.id)).size, 6)
  for (const theme of THEMES) {
    assert.equal(isTheme(theme.id), true)
    assert.equal(applyTheme(theme.id), theme.id)
    assert.equal(readTheme(), theme.id)
    assert.equal(values.get('access_token'), 'existing-session')
    assert.equal(values.get('refresh_token'), 'existing-refresh')
  }
})

test('navy uses the dark component scheme and switching back removes it', () => {
  const { root, classes } = fixture()
  for (const id of ['navy', 'black', 'lavender', 'sage', 'blue', 'navy', 'wine']) {
    applyTheme(id)
    const theme = THEMES.find(item => item.id === id)
    assert.equal(classes.has('dark'), theme.scheme === 'dark', id)
    assert.equal(root.style.colorScheme, theme.scheme, id)
    assert.equal(root.style.backgroundColor, theme.colors[0], id)
  }
})

test('storage-disabled browsers can still switch themes without an exception', () => {
  const { root, classes } = fixture(undefined, true)
  assert.equal(readTheme(), 'blue')
  assert.equal(applyTheme('navy'), 'navy')
  assert.equal(root.dataset.theme, 'navy')
  assert.equal(classes.has('dark'), true)
})

test('cross-tab restoration does not write back or change unrelated storage', () => {
  const { values } = fixture('sage')
  const before = [...values]
  applyTheme('navy', { persist: false })
  assert.equal(document.documentElement.dataset.theme, 'navy')
  assert.deepEqual([...values], before)
})

const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8')
const bootstrap = html.match(/<script id="theme-bootstrap">([\s\S]*?)<\/script>/)?.[1]
test('the synchronous first-paint bootstrap matches every runtime palette', () => {
  assert.ok(bootstrap, 'theme restoration must run before the application module')
  for (const theme of THEMES) {
    const { root, classes, storage, values } = fixture(theme.id)
    const before = [...values]
    runInNewContext(bootstrap, { document: { documentElement: root }, localStorage: storage })
    assert.equal(root.dataset.theme, theme.id)
    assert.equal(root.style.colorScheme, theme.scheme)
    assert.equal(root.style.backgroundColor, theme.colors[0])
    assert.equal(classes.has('dark'), theme.scheme === 'dark')
    assert.deepEqual([...values], before)
  }
})

test('first paint safely falls back with invalid or blocked theme storage', () => {
  for (const [saved, blocked] of [[undefined, false], ['__proto__', false], ['old-theme', false], ['navy', true]]) {
    const { root, storage } = fixture(saved, blocked)
    runInNewContext(bootstrap, { document: { documentElement: root }, localStorage: storage })
    assert.equal(root.dataset.theme, 'blue')
    assert.equal(root.style.colorScheme, 'light')
  }
})

const css = readFileSync(new URL('../src/styles/variables.css', import.meta.url), 'utf8')
const tokens = text => Object.fromEntries([...text.matchAll(/(--color-[\w-]+):\s*(#[\da-f]{6});/gi)].map(match => [match[1], match[2]]))
const defaults = tokens(css.slice(0, css.indexOf('}')))
function luminance(hex) {
  const rgb = hex.slice(1).match(/../g).map(value => parseInt(value, 16) / 255)
    .map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4)
  return rgb.reduce((total, value, index) => total + value * [0.2126, 0.7152, 0.0722][index], 0)
}
function contrast(a, b) {
  const values = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (values[0] + 0.05) / (values[1] + 0.05)
}
for (const id of ['sage', 'lavender', 'navy']) {
  test(`${id}: body, secondary, placeholder, primary buttons and status labels meet 4.5:1`, () => {
    const block = css.match(new RegExp(`:root\\[data-theme='${id}'\\]\\s*\\{([^}]+)\\}`))
    assert.ok(block, `palette ${id} has styling`)
    const palette = { ...defaults, ...tokens(block[1]) }
    const pairs = []
    for (const text of ['text-primary', 'text-regular', 'text-secondary', 'text-placeholder']) {
      for (const background of ['bg-page', 'bg-card', 'bg-soft']) pairs.push([text, background])
    }
    pairs.push(['on-primary', 'primary'], ['on-primary', 'primary-hover'], ['primary', 'primary-light'])
    for (const state of ['success', 'warning', 'danger']) pairs.push([state, `${state}-light`], [state, 'bg-card'])
    pairs.push(['info', 'bg-soft'])
    const results = pairs.map(([foreground, background]) => {
      const ratio = contrast(palette[`--color-${foreground}`], palette[`--color-${background}`])
      assert.ok(ratio >= 4.5, `${id} ${foreground}/${background}: ${ratio.toFixed(2)}:1`)
      return ratio
    })
    console.log(`${id} minimum token contrast ${Math.min(...results).toFixed(2)}:1`)
  })
}
