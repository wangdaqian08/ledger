import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, handleUnauthorized } from '../src/lib/api'

/** The client's three duties: echo the CSRF cookie, surface the server's reason, signal 401s. */
describe('api client', () => {
  const fetchMock = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    fetchMock.mockReset()
    document.cookie = 'LEDGER-XSRF=token-123'
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
    respond(200, { trips: [], overalls: [], settledTripCount: 0 })

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

  it('prefixes every call with the build base, so a sub-path deploy reaches its own API', async () => {
    // The base is baked in at module load, so a fresh import is needed to see the stubbed value.
    vi.stubEnv('BASE_URL', '/ledger/')
    vi.resetModules()
    const { api: rebased } = await import('../src/lib/api')
    respond(200, { trips: [], overalls: [], settledTripCount: 0 })

    await rebased.trips()

    expect(fetchMock.mock.calls[0]![0]).toBe('/ledger/api/trips')
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('serialises the body it was given, verbatim', async () => {
    respond(201, { id: 'x' })

    await api.submitSettlement('trip-1', { toMemberId: 'm-2', amountMinor: 6_000 })

    const [path, options] = fetchMock.mock.calls[0]!
    expect(path).toBe('/api/trips/trip-1/settlements')
    expect(JSON.parse(options.body)).toEqual({ toMemberId: 'm-2', amountMinor: 6_000 })
  })

  it('passes a receipt upload through as FormData, letting the browser set the boundary', async () => {
    respond(200, { id: 'i-9' })
    const image = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' })

    await api.uploadReceipt('i-9', image, 'receipt.jpg')

    const [path, options] = fetchMock.mock.calls[0]!
    expect(path).toBe('/api/items/i-9/receipt')
    expect(options.body).toBeInstanceOf(FormData)
    // Stamping application/json — or any Content-Type — onto a FormData body would strip the
    // multipart boundary and the server would see an unreadable request.
    expect(options.headers['Content-Type']).toBeUndefined()
    expect(options.headers['X-XSRF-TOKEN']).toBe('token-123')
  })
})
