import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api, ApiError, type MeView } from '@/lib/api'

/** Who is signed in, if anyone. The server's session cookie is the truth; this mirrors it. */
export const useSession = defineStore('session', () => {
  const me = ref<MeView | null>(null)
  const checked = ref(false)

  /** Ask once at startup; a 401 here just means the sign-in screen, not an error. */
  async function load() {
    try {
      me.value = await api.me()
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error
      me.value = null
    } finally {
      checked.value = true
    }
  }

  async function signIn(name: string) {
    me.value = await api.signIn(name)
    checked.value = true
  }

  async function signOut() {
    await api.signOut()
    me.value = null
  }

  return { me, checked, load, signIn, signOut }
})
