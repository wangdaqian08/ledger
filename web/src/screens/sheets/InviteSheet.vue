<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PersonAvatar from '@/components/PersonAvatar.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyBadge from '@/components/TallyBadge.vue'
import TallyButton from '@/components/TallyButton.vue'
import TextField from '@/components/TextField.vue'
import { api, type TripView } from '@/lib/api'

/**
 * The roster, and the two ways onto it: the creator writes a name down, and the share link lets
 * that person claim it. A trip's people exist before their owners do — friends who never sign in
 * still take a full share, ticked off by the payer (spec §4).
 */
const props = defineProps<{ open: boolean; trip: TripView }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()

const newName = ref('')
const linkNote = ref('')
const error = ref('')
const busy = ref(false)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    newName.value = ''
    linkNote.value = ''
    error.value = ''
  },
)

async function addName() {
  if (!newName.value.trim() || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.addMember(props.trip.id, newName.value.trim())
    newName.value = ''
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

async function copyLink() {
  error.value = ''
  try {
    const issued = await api.invite(props.trip.id)
    // The token rides in the fragment: browsers keep fragments out of server logs and Referer
    // headers, which is where a query-string token would leak.
    const link = `${location.origin}/join/${props.trip.id}#token=${issued.token}`
    try {
      await navigator.clipboard.writeText(link)
      linkNote.value = t('trip.linkCopied')
    } catch {
      linkNote.value = link
    }
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  }
}
</script>

<template>
  <SheetPanel :open="open" :title="t('invite.title')" @close="emit('close')">
    <div class="invite">
      <section class="invite__section">
        <div
          v-for="member in trip.members"
          :key="member.id"
          class="invite__member"
          data-testid="invite-member"
        >
          <PersonAvatar :name="member.displayName" :hue="member.personHue" :size="36" />
          <span class="invite__name">{{ member.isYou ? 'You' : member.displayName }}</span>
          <TallyBadge :tone="member.claimed ? 'settled' : 'pending'">
            {{ member.claimed ? t('invite.claimed') : t('invite.unclaimed') }}
          </TallyBadge>
        </div>
      </section>

      <form class="invite__add" @submit.prevent="addName">
        <TextField
          v-model="newName"
          test-id="member-name"
          :placeholder="t('invite.namePlaceholder')"
          :disabled="busy"
        />
        <TallyButton
          type="submit"
          variant="secondary"
          data-testid="add-member"
          :disabled="!newName.trim() || busy"
          @click="addName"
        >
          {{ t('invite.add') }}
        </TallyButton>
      </form>

      <TallyButton variant="primary" full-width data-testid="copy-link" @click="copyLink">
        {{ t('invite.copyLink') }}
      </TallyButton>

      <p v-if="linkNote" class="invite__note" data-testid="invite-note">{{ linkNote }}</p>
      <p v-if="error" class="invite__error" role="alert">{{ error }}</p>
    </div>
  </SheetPanel>
</template>

<style scoped>
.invite {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.invite__section {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.invite__member {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.invite__name {
  flex: 1;
  min-width: 0;
  overflow-wrap: break-word;
  font-weight: var(--weight-semibold);
  color: var(--text-body);
}

.invite__add {
  display: flex;
  gap: var(--space-2);
  align-items: center;
}

.invite__add > :first-child {
  flex: 1;
}

.invite__note {
  padding: var(--space-2) var(--space-3);
  border: var(--border-card);
  border-radius: var(--radius-md);
  background: var(--mint-tint);
  font-size: var(--text-caption);
  color: var(--ink-2);
  overflow-wrap: anywhere;
}

.invite__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
