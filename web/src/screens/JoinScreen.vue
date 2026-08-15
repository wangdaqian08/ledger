<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import PersonAvatar from '@/components/PersonAvatar.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyCard from '@/components/TallyCard.vue'
import { api, ApiError, type ClaimableView } from '@/lib/api'
import { useSession } from '@/stores/session'

/**
 * The share link's landing page, in one of three states decided by what the server says of the
 * viewer: already aboard (offer the trip, never the list — every claim could only be refused),
 * names still free (pick yours), or none left.
 *
 * Whoever is signed in is named at the foot, with a sign-out that leads back to this URL: a claim
 * binds the *account*, and on a shared or long-lived browser the account is the one fact the
 * person standing here cannot otherwise see.
 *
 * The token is the authorisation and travels in the URL fragment — never the query string, which
 * would put a claim on the trip into every access log and Referer header on the way here.
 */
const props = defineProps<{ tripId: string }>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSession()

const token = ref('')
const claimable = ref<ClaimableView | null>(null)
const chosen = ref<string | null>(null)
const error = ref('')
const busy = ref(false)

onMounted(async () => {
  token.value = new URLSearchParams(route.hash.replace(/^#/, '')).get('token') ?? ''
  if (!token.value) {
    error.value = t('join.badLink')
    return
  }
  // Alongside, not before: the footer naming the account can arrive late, the list cannot wait
  // on it. A failure here only costs that footer, so it is let go rather than surfaced.
  if (!session.checked) session.load().catch(() => {})
  try {
    claimable.value = await api.claimable(props.tripId, token.value)
  } catch (failure) {
    // A 401 has already bounced to sign-in via the global handler, keeping this URL as `next`.
    if (!(failure instanceof ApiError && failure.status === 401)) {
      error.value = failure instanceof Error ? failure.message : String(failure)
    }
  }
})

async function claim() {
  if (!chosen.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.claim(props.tripId, token.value, chosen.value)
    await router.push({ name: 'trip', params: { tripId: props.tripId } })
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

function openTrip() {
  return router.push({ name: 'trip', params: { tripId: props.tripId } })
}

/** `next` carries the whole invite URL, fragment included, so the right person lands straight back here. */
async function signOut() {
  await session.signOut()
  await router.push({ name: 'signin', query: { next: route.fullPath } })
}
</script>

<template>
  <main class="join">
    <TallyCard class="join__card">
      <h1 class="join__title">{{ t('join.title', { trip: claimable?.tripName ?? '…' }) }}</h1>

      <template v-if="claimable">
        <template v-if="claimable.you">
          <p class="join__aboard" data-testid="join-already">
            {{ t('join.alreadyOn', { name: claimable.you.displayName }) }}
          </p>
          <TallyButton variant="primary" full-width data-testid="join-open" @click="openTrip">
            {{ t('join.openTrip', { trip: claimable.tripName }) }}
          </TallyButton>
        </template>

        <p v-else-if="claimable.members.length === 0" class="join__empty">{{ t('join.allClaimed') }}</p>

        <template v-else>
          <h2 class="join__label">{{ t('join.pickYourName') }}</h2>
          <button
            v-for="member in claimable.members"
            :key="member.id"
            type="button"
            class="join__member"
            data-testid="join-member"
            :class="{ 'join__member--on': chosen === member.id }"
            :aria-pressed="chosen === member.id"
            @click="chosen = member.id"
          >
            <PersonAvatar :name="member.displayName" :hue="member.personHue" :size="36" />
            <span class="join__name">{{ member.displayName }}</span>
          </button>

          <TallyButton
            variant="primary"
            full-width
            data-testid="join-claim"
            :disabled="!chosen || busy"
            @click="claim"
          >
            {{ t('join.claim') }}
          </TallyButton>
        </template>

        <p v-if="session.me" class="join__who" data-testid="join-who">
          {{ t('join.signedInAs', { name: session.me.displayName }) }}
          <button type="button" class="join__signout" data-testid="join-signout" @click="signOut">
            {{ t('join.notYou') }}
          </button>
        </p>
      </template>

      <p v-if="error" class="join__error" role="alert">{{ error }}</p>
    </TallyCard>
  </main>
</template>

<style scoped>
.join {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100dvh;
  padding: var(--space-6) var(--gutter-screen);
}

.join__card {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.join__title {
  font-size: var(--text-heading-lg);
  font-weight: var(--weight-black);
  letter-spacing: var(--ls-heading-lg);
  color: var(--ink);
  overflow-wrap: break-word;
}

.join__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

.join__member {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 2px solid var(--hairline-strong);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  cursor: pointer;
  text-align: left;
}

.join__member--on {
  border-color: var(--ink);
  background: var(--grape-tint);
}

.join__name {
  font-weight: var(--weight-semibold);
  color: var(--text-body);
  min-width: 0;
  overflow-wrap: break-word;
}

.join__empty {
  color: var(--text-muted);
}

.join__aboard {
  color: var(--text-body);
}

.join__who {
  color: var(--text-muted);
  font-size: var(--text-caption);
}

.join__signout {
  border: none;
  background: none;
  padding: 0;
  font: inherit;
  color: var(--text-body);
  text-decoration: underline;
  cursor: pointer;
}

.join__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
