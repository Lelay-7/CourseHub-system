import assert from 'node:assert/strict'
import test from 'node:test'
import { usePrivacySettings } from '../src/composables/usePrivacySettings.js'

const response = (contact = 2, phone = 1, email = 2) => ({ data: { contact_visible: contact, phone_visible: phone, email_visible: email } })
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b }); return { promise, resolve, reject } }
function setup({ read = async () => response(), update = async () => {} } = {}) {
  const identity = { value: 'A' }
  return { identity, p: usePrivacySettings({ getIdentity: () => identity.value, read, update }) }
}

test('failed initial read never enables edits and retry establishes the saved baseline', async () => {
  let fail = true
  const { p } = setup({ read: async () => { if (fail) throw Error('offline'); return response() } })
  assert.equal(p.canSave.value, false)
  await p.load()
  assert.equal(p.state.loadError, true)
  assert.equal(p.editable.value, false)
  fail = false
  await p.load()
  assert.equal(p.editable.value, true)
  assert.equal(p.dirty.value, false)
  p.draft.email_visible = 1
  assert.equal(p.canSave.value, true)
})

test('a failed save keeps the edited privacy draft and permits a retry', async () => {
  let fail = true
  const { p } = setup({ update: async () => { if (fail) throw Error('offline') } })
  await p.load(); p.draft.contact_visible = 3
  assert.equal(await p.save(), false)
  assert.equal(p.draft.contact_visible, 3)
  assert.equal(p.dirty.value, true)
  assert.equal(p.state.saveError, true)
  fail = false
  assert.equal(await p.save(), true)
  assert.equal(p.dirty.value, false)
})

test('a late read for A cannot overwrite the loaded settings for B', async () => {
  const a = deferred(); let count = 0
  const { identity, p } = setup({ read: () => ++count === 1 ? a.promise : Promise.resolve(response(3, 2, 1)) })
  const pending = p.load()
  identity.value = 'B'; p.resetForSession(); await p.load()
  a.resolve(response(1, 1, 1)); await pending
  assert.deepEqual({ ...p.draft }, response(3, 2, 1).data)
  assert.equal(p.state.owner, 'B')
  assert.equal(p.dirty.value, false)
})

test('a late successful save for A cannot mark B draft saved', async () => {
  const a = deferred()
  const { identity, p } = setup({ update: () => a.promise })
  await p.load(); p.draft.contact_visible = 3
  const pending = p.save()
  identity.value = 'B'; p.resetForSession(); await p.load(); p.draft.email_visible = 1
  a.resolve({ code: 200 })
  assert.equal(await pending, false)
  assert.equal(p.dirty.value, true)
  assert.equal(p.draft.email_visible, 1)
  assert.equal(p.state.saving, false)
})

test('duplicate saves submit once and saved baseline is the submitted snapshot', async () => {
  const pending = deferred(); let count = 0
  const { p } = setup({ update: () => { count++; return pending.promise } })
  await p.load(); p.draft.contact_visible = 3
  const one = p.save(), two = p.save()
  assert.equal(count, 1)
  // An external draft change while waiting must not be silently marked saved.
  p.draft.email_visible = 1
  pending.resolve({ code: 200 }); await Promise.all([one, two])
  assert.equal(p.dirty.value, true)
  p.discard()
  assert.deepEqual({ ...p.draft }, response(3, 1, 2).data)
})

test('logout and session replacement bypass unsaved prompts and cannot submit the old draft', async () => {
  let writes = 0
  const { identity, p } = setup({ update: async () => { writes++ } })
  await p.load(); p.draft.contact_visible = 3
  assert.equal(p.needsLeaveConfirmation(), true)
  identity.value = null
  assert.equal(p.needsLeaveConfirmation(), false)
  assert.equal(await p.save(), false)
  assert.equal(writes, 0)
  assert.equal(p.dirty.value, false)
  assert.deepEqual({ ...p.draft }, response(2, 2, 2).data)
})

test('unmount makes outstanding reads inert and removes the draft', async () => {
  const a = deferred()
  const { p } = setup({ read: () => a.promise })
  const pending = p.load(); p.dispose(); a.resolve(response(1, 1, 1)); await pending
  assert.equal(p.state.loaded, false)
  assert.equal(p.needsLeaveConfirmation(), false)
})
