// One in-flight task. Abort-ignoring transports are still awaited before restarting.
export function createChatPoller({ task, intervalMs, isVisible, setTimeout = globalThis.setTimeout, clearTimeout = globalThis.clearTimeout, onError = () => {} }) {
  let active = false, running = false, timer = null, controller = null, failures = 0, immediate = false
  function cancel() { if (timer !== null) clearTimeout(timer); timer = null; controller?.abort() }
  async function run() {
    if (!active || !isVisible()) return
    if (running) { immediate = true; return }
    running = true; immediate = false; controller = new AbortController()
    const signal = controller.signal
    try { await task(signal); if (!signal.aborted) failures = 0 }
    catch (error) { if (!signal.aborted) { failures++; onError(error) } }
    finally {
      running = false; controller = null
      if (active && isVisible()) {
        if (immediate) { immediate = false; void run() }
        else timer = setTimeout(() => { timer = null; void run() }, failures ? [10000,20000,40000,60000][Math.min(failures-1,3)] : intervalMs)
      }
    }
  }
  return {
    start() { if (active) return; active = true; failures = 0; void run() },
    stop() { active = false; immediate = false; cancel() },
    visibilityChanged() { cancel(); if (active && isVisible()) { immediate = true; void run() } }
  }
}
