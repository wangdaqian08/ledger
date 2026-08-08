<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { ApiError } from '@/lib/api'
import { useSession } from '@/stores/session'

/**
 * Screen 1. In production this is one Google button (build order step 10); against the dev
 * identity provider any name is an identity, so the dev build shows a name field and says so.
 */
const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const session = useSession()

const name = ref('')
const busy = ref(false)
const error = ref('')

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
    error.value = failure instanceof ApiError ? failure.message : t('signin.button')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="signin">
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
      <p class="signin__note">{{ t('signin.devNote') }}</p>
    </form>
  </main>
</template>

<style scoped>
.signin {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-8);
  min-height: 100dvh;
  padding: var(--space-6);
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
