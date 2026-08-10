/**
 * Today's calendar date as `YYYY-MM-DD`, in the viewer's own timezone.
 *
 * `new Date().toISOString()` is UTC, so in any negative-offset zone an evening expense would be
 * stamped with tomorrow's date — and the feed, which parses the string as a *local* calendar day,
 * would then file it under the wrong heading. `getFullYear`/`getMonth`/`getDate` read the local
 * clock (the system default timezone), which is the date the person actually means.
 */
export function todayLocal(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
