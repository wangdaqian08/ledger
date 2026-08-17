<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PersonAvatar from '@/components/PersonAvatar.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyBadge from '@/components/TallyBadge.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import TextField from '@/components/TextField.vue'
import { api, type TripView } from '@/lib/api'

/**
 * The roster, and the two ways onto it: the creator writes a name down, and the share link lets
 * that person claim it. A trip's people exist before their owners do — friends who never sign in
 * still take a full share, ticked off by the payer (spec §4).
 *
 * Names are the roster's one hand-entered fact, so they are the one that gets typed wrong — the
 * creator can fix one in place. Renaming moves no number; nothing financial hangs off a name.
 */
const props = defineProps<{ open: boolean; trip: TripView }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()
const router = useRouter()

const newName = ref('')
const linkNote = ref('')
const error = ref('')
const busy = ref(false)
const editingId = ref<string | null>(null)
const editedName = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    newName.value = ''
    linkNote.value = ''
    error.value = ''
    editingId.value = null
  },
)

function startRename(member: { id: string; displayName: string }) {
  editingId.value = member.id
  editedName.value = member.displayName
  error.value = ''
}

async function saveRename() {
  const memberId = editingId.value
  const name = editedName.value.trim()
  if (!memberId || !name || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.renameMember(props.trip.id, memberId, name)
    editingId.value = null
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

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

async function endTrip() {
  if (busy.value || !confirm(t('invite.endConfirm'))) return
  busy.value = true
  error.value = ''
  try {
    await api.closeTrip(props.trip.id)
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

async function reopenTrip() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.reopenTrip(props.trip.id)
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
    // headers, which is where a query-string token would leak. The path comes from the router so
    // it carries the build's base — under `/ledger` a hand-built root path would 404.
    const joinPath = router.resolve({ name: 'join', params: { tripId: props.trip.id } }).href
    const link = `${location.origin}${joinPath}#token=${issued.token}`
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

          <form
            v-if="editingId === member.id"
            class="invite__rename"
            data-testid="rename-form"
            @submit.prevent="saveRename"
          >
            <TextField v-model="editedName" test-id="rename-name" :disabled="busy" />
            <TallyButton
              type="submit"
              variant="secondary"
              size="sm"
              data-testid="rename-save"
              :disabled="!editedName.trim() || busy"
            >
              {{ t('common.save') }}
            </TallyButton>
            <TallyButton variant="ghost" size="sm" data-testid="rename-cancel" @click="editingId = null">
              {{ t('common.cancel') }}
            </TallyButton>
          </form>

          <template v-else>
            <span class="invite__name">{{ member.isYou ? t('common.you') : member.displayName }}</span>
            <button
              v-if="trip.youAreCreator"
              type="button"
              class="invite__edit"
              data-testid="rename-member"
              :aria-label="t('invite.rename', { name: member.displayName })"
              @click="startRename(member)"
            >
              <TallyIcon name="pencil" :size="16" />
            </button>
            <TallyBadge :tone="member.claimed ? 'settled' : 'pending'">
              {{ member.claimed ? t('invite.claimed') : t('invite.unclaimed') }}
            </TallyBadge>
          </template>
        </div>
      </section>

      <!-- Adding names and handing out the link is the creator's job (the server enforces it); a
           plain member sees the roster but not the write controls, so no button leads to a 403. -->
      <template v-if="trip.youAreCreator">
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

        <div class="invite__lifecycle">
          <!-- Ending is reversible and blocks only the spending record — but it also starts the
               14-day clock on receipt photos, which is why the confirm names it. -->
          <TallyButton
            v-if="!trip.closedAt"
            variant="danger"
            size="sm"
            data-testid="end-trip"
            :disabled="busy"
            @click="endTrip"
          >
            {{ t('invite.endTrip') }}
          </TallyButton>
          <TallyButton
            v-else
            variant="secondary"
            size="sm"
            data-testid="reopen-trip"
            :disabled="busy"
            @click="reopenTrip"
          >
            {{ t('invite.reopenTrip') }}
          </TallyButton>
          <p class="invite__hint">{{ trip.closedAt ? t('invite.endedNote') : t('invite.endNote') }}</p>
        </div>
      </template>

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

.invite__rename {
  display: flex;
  flex: 1;
  gap: var(--space-2);
  align-items: center;
  min-width: 0;
}

.invite__rename > :first-child {
  flex: 1;
  min-width: 0;
}

.invite__edit {
  display: grid;
  place-items: center;
  padding: var(--space-1);
  border: none;
  background: none;
  color: var(--text-muted);
  cursor: pointer;
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

.invite__lifecycle {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1.5px solid var(--hairline);
}

.invite__hint {
  font-size: var(--text-caption);
  color: var(--text-muted);
}

.invite__error {
  color: var(--coral);
  font-size: var(--text-caption);
}
</style>
