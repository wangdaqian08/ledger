import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, type TripsView, type TripView } from '@/lib/api'

/**
 * Trips as the server last told them. Every number in here is derived by the engine on read, so
 * after any mutation the trip is re-fetched whole rather than patched in place — a locally
 * adjusted total is exactly the stale number the whole design exists to avoid.
 */
export const useTrips = defineStore('trips', () => {
  const overview = ref<TripsView | null>(null)
  const byId = ref<Record<string, TripView>>({})

  async function loadOverview() {
    overview.value = await api.trips()
  }

  async function loadTrip(tripId: string): Promise<TripView> {
    const trip = await api.trip(tripId)
    byId.value[tripId] = trip
    return trip
  }

  async function createTrip(body: { name: string; icon: string; hue: number; currencyCode: string }) {
    const trip = await api.createTrip(body)
    byId.value[trip.id] = trip
    await loadOverview()
    return trip
  }

  return { overview, byId, loadOverview, loadTrip, createTrip }
})
