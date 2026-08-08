<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import PersonAvatar from '@/components/PersonAvatar.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyCard from '@/components/TallyCard.vue'
import { api, ApiError, type ClaimableView } from '@/lib/api'

/**
 * The share link's landing page: the trip's unclaimed names, pick yours, and you are on it.
 *
 * The token is the authorisation and travels in the URL fragment — never the query string, which
 * would put a claim on the trip into every access log and Referer header on the way here.
 */
const props = defineProps<{ tripId: string }>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

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
</script>

<template>
  <main class="join">
    <TallyCard class="join__card">
      <h1 class="join__title">{{ t('join.title', { trip: claimable?.tripName ?? '…' }) }}</h1>

      <template v-if="claimable">
        <p v-if="claimable.members.length === 0" class="join__empty">{{ t('join.allClaimed') }}</p>

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

.join__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
