import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, handleUnauthorized } from '../src/lib/api'

/** The client's three duties: echo the CSRF cookie, surface the server's reason, signal 401s. */
describe('api client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    document.cookie = 'XSRF-TOKEN=token-123'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    handleUnauthorized(() => {})
  })

  function respond(status: number, body?: unknown) {
    fetchMock.mockResolvedValueOnce({
      ok: status >= 200 && status < 300,
      status,
      json: () => (body === undefined ? Promise.reject(new Error('no body')) : Promise.resolve(body)),
    })
  }

  it('echoes the CSRF cookie in the header Spring expects', async () => {
    respond(200, { trips: [], overallNetMinor: 0, settledTripCount: 0 })

    await api.trips()

    const [, options] = fetchMock.mock.calls[0]!
    expect(options.headers['X-XSRF-TOKEN']).toBe('token-123')
    expect(options.credentials).toBe('same-origin')
  })

  it("surfaces the server's own sentence from a problem detail", async () => {
    respond(400, { detail: 'The exact amounts add up to 9000, but the expense is 10000' })

    await expect(api.trips()).rejects.toMatchObject({
      status: 400,
      message: 'The exact amounts add up to 9000, but the expense is 10000',
    })
  })

  it('tells the app when a session has ended, exactly once per call', async () => {
    const bounced = vi.fn()
    handleUnauthorized(bounced)
    respond(401)

    await expect(api.me()).rejects.toBeInstanceOf(ApiError)
    expect(bounced).toHaveBeenCalledTimes(1)
  })

  it('serialises the body it was given, verbatim', async () => {
    respond(201, { id: 'x' })

    await api.submitSettlement('trip-1', { toMemberId: 'm-2', amountMinor: 6_000 })

    const [path, options] = fetchMock.mock.calls[0]!
    expect(path).toBe('/api/trips/trip-1/settlements')
    expect(JSON.parse(options.body)).toEqual({ toMemberId: 'm-2', amountMinor: 6_000 })
  })
})
