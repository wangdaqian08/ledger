import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type TripsView } from '@/lib/api'

/**
 * Trips as the server last told them. Every number in here is derived by the engine on read, so
 * after any mutation the trip is re-fetched whole rather than patched in place — a locally
 * adjusted total is exactly the stale number the whole design exists to avoid.
 */
export const useTrips = defineStore('trips', () => {
  const overview = ref<TripsView | null>(null)

  async function loadOverview() {
    overview.value = await api.trips()
  }

  async function createTrip(body: { name: string; icon: string; hue: number; currencyCode: string }) {
    const trip = await api.createTrip(body)
    await loadOverview()
    return trip
  }

  return { overview, loadOverview, createTrip }
})
