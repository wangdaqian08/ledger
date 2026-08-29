<script setup lang="ts">
import { computed, useId, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import TallyIcon from './TallyIcon.vue'
import { COMMENT_MAX_CHARS, COMMENT_MAX_WORDS, countWords } from '@/lib/words'

/**
 * An optional comment on a bill, folded away until somebody wants it.
 *
 * Collapsed by default so neither the add sheet nor the detail sheet grows a box most expenses
 * never need.
 *
 * The fold is a model, not private state. The detail sheet draws its own Save and Discard beside
 * this field, so a fold only the field knew about left those buttons over a box that was no longer
 * there — and, worse, over an invisible draft that Save would still write. One value, one owner.
 *
 * Past the word limit the counter turns red and the parent is told — the text itself is left
 * alone. Truncating somebody mid-sentence to enforce a limit they have not finished breaking is
 * worse than letting them see the count and cut it back themselves.
 */
const model = defineModel<string>({ required: true })
/** Whether the box is unfolded. The parent's, so the two can never hold different answers. */
const open = defineModel<boolean>('open', { default: false })
/**
 * Whether the text is past the limit. The parent gates its Save on this rather than counting again
 * itself: one implementation of the limit, and the button can never disagree with the counter.
 */
const invalid = defineModel<boolean>('invalid', { default: false })

const { t } = useI18n()

// The label names the box, the count describes it, and the toggle says what it controls. None of
// the three can be expressed in markup here: the label is inside a <button>, so no <label for>
// reaches the textarea, and the placeholder is the accessible-name algorithm's last resort — the
// one thing that disappears the moment somebody types.
const labelId = useId()
const countId = useId()
const panelId = useId()

const words = computed(() => countWords(model.value))
const chars = computed(() => model.value.length)
const over = computed(() => words.value > COMMENT_MAX_WORDS)

/**
 * Which of the two limits this text will reach first, cross-multiplied so it stays whole numbers.
 * A spaceless script counts as one word however long it runs, which leaves the word counter frozen
 * and the character backstop — the bound that will actually stop the typing — completely silent.
 * Counting whichever is nearer is locale-independent: Chinese typed in an English browser has the
 * same problem, and English prose still measures against the words.
 */
const boundByChars = computed(() => chars.value * COMMENT_MAX_WORDS > words.value * COMMENT_MAX_CHARS)

const countLabel = computed(() =>
  boundByChars.value
    ? t('comment.countChars', { count: chars.value, max: COMMENT_MAX_CHARS })
    : t('comment.count', { count: words.value, max: COMMENT_MAX_WORDS }),
)

watch(over, (bad) => (invalid.value = bad), { immediate: true })
</script>

<template>
  <div class="comment">
    <button
      type="button"
      class="comment__toggle"
      data-testid="comment-toggle"
      :aria-expanded="open"
      :aria-controls="panelId"
      @click="open = !open"
    >
      <span :id="labelId" class="comment__label">{{ t('comment.add') }}</span>
      <TallyIcon
        name="chevron-right"
        :size="16"
        class="comment__chevron"
        :class="{ 'comment__chevron--open': open }"
      />
    </button>

    <!-- Something for aria-controls to point at whether or not the box is up; `display: contents`
         keeps it out of the layout, so the rows below stay exactly where they were. -->
    <div :id="panelId" class="comment__panel">
      <textarea
        v-if="open"
        v-model="model"
        class="comment__input"
        :class="{ 'comment__input--over': over }"
        data-testid="comment-input"
        rows="3"
        :placeholder="t('comment.placeholder')"
        :maxlength="COMMENT_MAX_CHARS"
        :aria-labelledby="labelId"
        :aria-describedby="countId"
        :aria-invalid="over ? 'true' : undefined"
      ></textarea>
      <!-- Past the limit the count stays on screen even folded away: it is the only thing that
           explains the disabled Save, and that Save may be a long scroll below here.
           Not a live region — it would queue "1/100 words", "2/100 words" behind the screen
           reader's own echo of every keystroke. aria-describedby carries it to the box instead. -->
      <span
        v-if="open || over"
        :id="countId"
        class="comment__count"
        :class="{ 'comment__count--over': over }"
        data-testid="comment-count"
        aria-live="off"
      >
        {{ countLabel }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.comment {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.comment__toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  text-align: left;
}

/* The same uppercase micro-label as RECEIPT and WHO PAID, so it reads as one more section header
   rather than a control that wandered in. */
.comment__label {
  font-size: var(--text-label);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--ls-label);
  text-transform: uppercase;
  color: var(--text-muted);
}

/*
 * Points down when there is something to open and up once it is open. `icons.ts` carries no
 * chevron-down, so this is the shared `chevron-right` turned a quarter each way — the same trick
 * AmountKeypadField uses, which is why the icon name reads sideways to the direction shown.
 */
.comment__chevron {
  transition: transform var(--dur-fast) var(--ease-out);
  transform: rotate(90deg);
  color: var(--ink-2);
}

.comment__chevron--open {
  transform: rotate(-90deg);
}

/* An anchor for aria-controls and nothing else: its children lay out as if it were not there. */
.comment__panel {
  display: contents;
}

.comment__input {
  padding: var(--space-3);
  background: var(--surface-card);
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  font-family: var(--font-core);
  font-size: var(--text-body);
  color: var(--ink);
  resize: vertical;
}

.comment__input--over {
  border-color: var(--coral);
}

.comment__count {
  align-self: flex-end;
  font-size: var(--text-caption);
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.comment__count--over {
  color: var(--coral);
  font-weight: var(--weight-semibold);
}
</style>
