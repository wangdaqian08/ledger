<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import PayIdLine from '@/components/PayIdLine.vue'
import PersonAvatar from '@/components/PersonAvatar.vue'
import SheetPanel from '@/components/SheetPanel.vue'
import TallyBadge from '@/components/TallyBadge.vue'
import TallyButton from '@/components/TallyButton.vue'
import TallyIcon from '@/components/TallyIcon.vue'
import TextField from '@/components/TextField.vue'
import { api, type TripView } from '@/lib/api'
import { currencySymbol, formatMinor } from '@/lib/money'
import { useSession } from '@/stores/session'
import { useTrips } from '@/stores/trips'

/**
 * The roster, and the two ways onto it: the creator writes a name down, and the share link lets
 * that person claim it. A trip's people exist before their owners do — friends who never sign in
 * still take a full share, ticked off by the payer (spec §4).
 *
 * Names are the roster's one hand-entered fact, so they are the one that gets typed wrong — the
 * creator can fix one in place. Renaming moves no number; nothing financial hangs off a name.
 *
 * Each person's PayID shows under their name. It belongs to the account, not the trip, so your own
 * row is the only one with an editor — and it changes what every group you are in sees. Everyone
 * else's is read-only with a Copy, badged for a week after it changes (sign-in is a bare name, so
 * a fresh PayID is worth a second look before paying it). An unclaimed seat has no account yet.
 */
const props = defineProps<{ open: boolean; trip: TripView }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()
const router = useRouter()
const trips = useTrips()
const session = useSession()

const newName = ref('')
const linkNote = ref('')
const error = ref('')
const busy = ref(false)
const editingId = ref<string | null>(null)
const activeAction = ref<'add' | 'payid' | null>(null)
const editedName = ref('')
const editingPayId = ref(false)
const payIdDraft = ref('')
const payIdError = ref('')

watch(
  () => props.open,
  (open) => {
    if (!open) return
    newName.value = ''
    linkNote.value = ''
    error.value = ''
    editingId.value = null
    editingPayId.value = false
    payIdError.value = ''
  },
)

function startPayIdEdit(current: string | null) {
  payIdDraft.value = current ?? ''
  payIdError.value = ''
  editingPayId.value = true
}

/** Blank clears it; the server trims too, but sending what will be stored keeps the two in step. */
async function savePayId() {
  if (busy.value) return
  busy.value = true
  activeAction.value = 'payid'
  payIdError.value = ''
  try {
    session.me = await api.setPayId(payIdDraft.value.trim() || null)
    editingPayId.value = false
    emit('changed')
  } catch (failure) {
    payIdError.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
    activeAction.value = null
  }
}

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
  activeAction.value = 'add'
  error.value = ''
  try {
    await api.addMember(props.trip.id, newName.value.trim())
    newName.value = ''
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
    activeAction.value = null
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

async function putAway() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.hideTrip(props.trip.id)
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

async function putBack() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await api.unhideTrip(props.trip.id)
    emit('changed')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
  } finally {
    busy.value = false
  }
}

/**
 * The one act that reaches every other member's app, so it is the one that states its cost first.
 * `unsettledMinor` is the whole group's open money, not just the viewer's own net — a host who is
 * personally square must still be told what the others have unsettled, and naming it here is the
 * last moment a warning can still change the outcome, since the server deliberately does not
 * refuse a delete over outstanding money (that would trap an abandoned trip forever).
 */
async function deleteTrip() {
  if (busy.value) return
  const outstanding = props.trip.unsettledMinor
  const question = outstanding
    ? t('invite.deleteConfirmOutstanding', {
        name: props.trip.name,
        amount: formatMinor(outstanding, {
          currencyCode: props.trip.currencyCode,
          symbol: currencySymbol(props.trip.currencyCode),
        }),
      })
    : t('invite.deleteConfirm', { name: props.trip.name })
  if (!confirm(question)) return

  busy.value = true
  error.value = ''
  try {
    await api.deleteTrip(props.trip.id)
    // Refresh before navigating: home would otherwise flash its cached list, deleted trip still
    // on it as a live card, until the re-fetch resolved.
    await trips.loadOverview()
    // Home is the only place left that knows about this trip — the Recently deleted section is
    // where it can be brought back from, and staying on a screen for a deleted trip would 404.
    await router.push({ name: 'trips' })
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : String(failure)
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

          <div class="invite__body">
            <div class="invite__head">
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

            <template v-if="member.isYou">
              <form
                v-if="editingPayId"
                class="invite__payid-form"
                data-testid="payid-form"
                @submit.prevent="savePayId"
              >
                <TextField
                  v-model="payIdDraft"
                  test-id="payid-input"
                  :label="t('payId.label')"
                  :placeholder="t('payId.placeholder')"
                  :error="payIdError || undefined"
                  :disabled="busy"
                />
                <div class="invite__payid-actions">
                  <TallyButton
                    type="submit"
                    variant="secondary"
                    size="sm"
                    data-testid="payid-save"
                    :loading="activeAction === 'payid'"
                    :disabled="busy"
                    @click="savePayId"
                  >
                    {{ t('common.save') }}
                  </TallyButton>
                  <TallyButton
                    variant="ghost"
                    size="sm"
                    data-testid="payid-cancel"
                    @click="editingPayId = false"
                  >
                    {{ t('common.cancel') }}
                  </TallyButton>
                </div>
              </form>
              <PayIdLine
                v-else-if="member.payId"
                :pay-id="member.payId"
                :recently-changed="false"
                :owner-name="member.displayName"
                :copyable="false"
              >
                <button
                  type="button"
                  class="invite__edit"
                  data-testid="payid-edit"
                  :aria-label="t('payId.edit')"
                  @click="startPayIdEdit(member.payId)"
                >
                  <TallyIcon name="pencil" :size="16" />
                </button>
              </PayIdLine>
              <TallyButton
                v-else
                class="invite__payid-add"
                variant="secondary"
                size="sm"
                data-testid="payid-add"
                @click="startPayIdEdit(null)"
              >
                {{ t('payId.add') }}
              </TallyButton>
              <p class="invite__hint" data-testid="payid-hint">{{ t('payId.hint') }}</p>
            </template>
            <PayIdLine
              v-else-if="member.claimed"
              :pay-id="member.payId"
              :recently-changed="member.payIdChangedRecently"
              :owner-name="member.displayName"
            />
            <span v-else class="invite__payid-none" data-testid="payid-unclaimed">
              {{ t('payId.noneYet') }}
            </span>
          </div>
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
            :loading="activeAction === 'add'"
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

          <!-- Putting away is only offered once the trip has ended: a live trip vanishing from
               twelve home screens has no good reason to happen, and the server refuses it. -->
          <template v-if="trip.closedAt">
            <TallyButton
              v-if="!trip.hiddenAt"
              variant="secondary"
              size="sm"
              data-testid="put-away-trip"
              :disabled="busy"
              @click="putAway"
            >
              {{ t('invite.putAway') }}
            </TallyButton>
            <TallyButton
              v-else
              variant="secondary"
              size="sm"
              data-testid="put-back-trip"
              :disabled="busy"
              @click="putBack"
            >
              {{ t('invite.putBack') }}
            </TallyButton>
            <p class="invite__hint">
              {{ trip.hiddenAt ? t('invite.putBackNote') : t('invite.putAwayNote') }}
            </p>
          </template>

          <!-- On a live trip the control is shown disabled, not hidden: absence reads as a missing
               feature, while a disabled button with a reason teaches the rule (end it first). -->
          <template v-else>
            <TallyButton variant="secondary" size="sm" data-testid="put-away-locked" :disabled="true">
              {{ t('invite.putAwayLocked') }}
            </TallyButton>
            <p class="invite__hint">{{ t('invite.putAwayLockedNote') }}</p>
          </template>

          <TallyButton
            variant="danger"
            size="sm"
            data-testid="delete-trip"
            :disabled="busy"
            @click="deleteTrip"
          >
            {{ t('invite.deleteTrip') }}
          </TallyButton>
          <p class="invite__hint">{{ t('invite.deleteNote') }}</p>
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
  align-items: flex-start;
  gap: var(--space-3);
}

.invite__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-1);
  min-width: 0;
}

/* The name line keeps the avatar's height, so a short roster row still reads as one line. */
.invite__head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-height: 36px;
}

.invite__payid-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.invite__payid-actions {
  display: flex;
  gap: var(--space-2);
}

.invite__payid-add {
  align-self: flex-start;
}

.invite__payid-none {
  font-size: var(--text-caption);
  color: var(--text-muted);
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
