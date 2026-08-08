import { createRouter, createWebHistory } from 'vue-router'
import { handleUnauthorized } from '@/lib/api'

/**
 * Four routes; the sheets are not among them. Add-expense, item detail, claim and settle-up open
 * over the trip screen as sheets, matching the demo — a sheet is a conversation on top of a
 * screen, not a place, and Back must close it rather than leave the trip.
 */
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/signin', name: 'signin', component: () => import('@/screens/SignInScreen.vue') },
    { path: '/', name: 'trips', component: () => import('@/screens/TripsScreen.vue') },
    {
      path: '/trips/:tripId',
      name: 'trip',
      component: () => import('@/screens/TripScreen.vue'),
      props: true,
    },
    // The share link. The token travels in the URL fragment, which browsers keep out of request
    // logs and Referer headers alike.
    { path: '/join/:tripId', name: 'join', component: () => import('@/screens/JoinScreen.vue'), props: true },
  ],
})

// Any 401, anywhere, means the same thing: show the sign-in screen, remember where they were going.
handleUnauthorized(() => {
  const current = router.currentRoute.value
  if (current.name !== 'signin') {
    router.push({ name: 'signin', query: { next: current.fullPath } })
  }
})
