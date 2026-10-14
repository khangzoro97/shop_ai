import { tokenStore } from './api.ts'

export type FunnelEventName = 'VISIT' | 'CLICK_CREATE' | 'TRIAL' | 'CLICK_ZALO' | 'DOWNLOAD'

const VISITOR_KEY = 'fortfio.visitor'
const VISIT_KEY = 'fortfio.visited'

function visitorId() {
  let id = localStorage.getItem(VISITOR_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(VISITOR_KEY, id)
  }
  return id
}

/** Fire-and-forget; `keepalive` lets the request finish when the click navigates away. */
export function track(name: FunnelEventName) {
  const token = tokenStore.get()
  fetch('/api/events', {
    method: 'POST',
    keepalive: true,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: JSON.stringify({ name, visitorId: visitorId() }),
  }).catch(() => {})
}

/** One visit per browser session, however many pages are opened. */
export function trackVisit() {
  if (sessionStorage.getItem(VISIT_KEY)) return
  sessionStorage.setItem(VISIT_KEY, '1')
  track('VISIT')
}

/** Counts clicks on any link into Studio, to Zalo or marked `data-track="download"`, wherever it sits. */
export function trackLinkClicks(event: MouseEvent) {
  const link = (event.target as Element | null)?.closest('a')
  if (!link) return
  const href = link.getAttribute('href') ?? ''
  if (link.dataset.track === 'download') track('DOWNLOAD')
  else if (href === '/studio') track('CLICK_CREATE')
  else if (href.startsWith('https://zalo.me')) track('CLICK_ZALO')
}
