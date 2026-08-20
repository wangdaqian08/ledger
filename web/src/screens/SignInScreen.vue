<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import LocaleToggle from '@/components/LocaleToggle.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { ApiError } from '@/lib/api'
import { useSession } from '@/stores/session'

/**
 * Screen 1. A name is the whole sign-in — the dev provider and production's interim `name-signin`
 * provider both work this way, and the note below says so honestly. Google Sign-In (build order
 * step 10) will replace the field with one button behind the same identity seam.
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSession()

const name = ref('')
const busy = ref(false)
const error = ref('')
const version = import.meta.env.VITE_APP_VERSION || 'dev'

onMounted(async () => {
  // Asking "who am I" does two jobs when this is the first page someone opens: the answer sends a
  // signed-in visitor straight through, and the *refusal* carries the CSRF cookie — without which
  // the sign-in POST below would itself be turned away.
  if (!session.checked) await session.load()
  if (session.me) {
    const next = typeof route.query.next === 'string' ? route.query.next : '/'
    await router.replace(next)
  }
})

async function submit() {
  if (!name.value.trim() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await session.signIn(name.value.trim())
    const next = typeof route.query.next === 'string' ? route.query.next : '/'
    await router.push(next)
  } catch (failure) {
    // A non-API failure (the network is down) is not the button's name — say what happened.
    error.value = failure instanceof ApiError ? failure.message : t('signin.failed')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="signin">
    <LocaleToggle class="signin__locale" />
    <p class="signin__version" data-testid="app-version">{{ version }}</p>
    <div class="signin__brand">
      <h1 class="signin__title">{{ t('signin.title') }}</h1>
      <p class="signin__tagline">{{ t('signin.tagline') }}</p>
    </div>

    <form class="signin__form" @submit.prevent="submit">
      <TextField
        v-model="name"
        test-id="signin-name"
        :placeholder="t('signin.namePlaceholder')"
        :disabled="busy"
      />
      <TallyButton
        type="submit"
        variant="primary"
        full-width
        data-testid="signin-submit"
        :disabled="!name.trim() || busy"
        @click="submit"
      >
        {{ t('signin.button') }}
      </TallyButton>
      <p v-if="error" class="signin__error" role="alert">{{ error }}</p>
      <p class="signin__note">{{ t('signin.note') }}</p>
    </form>
  </main>
</template>

<style scoped>
.signin {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-8);
  min-height: 100dvh;
  padding: var(--space-6);
}

.signin__locale {
  position: absolute;
  top: max(var(--space-4), env(safe-area-inset-top));
  right: var(--space-4);
}

.signin__version {
  position: absolute;
  bottom: max(var(--space-4), env(safe-area-inset-bottom));
  left: var(--space-4);
  color: var(--text-muted);
  font-size: var(--text-caption);
}

.signin__brand {
  text-align: center;
}

.signin__title {
  font-family: var(--font-core);
  font-size: var(--text-display);
  font-weight: var(--weight-black);
  letter-spacing: var(--ls-display);
  line-height: var(--lh-display);
  color: var(--ink);
}

.signin__tagline {
  margin-top: var(--space-2);
  font-family: var(--font-core);
  color: var(--text-muted);
}

.signin__form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.signin__error {
  color: var(--coral);
  font-size: var(--text-caption);
}

.signin__note {
  text-align: center;
  color: var(--text-muted);
  font-size: var(--text-caption);
}
</style>
