<script setup lang="ts">
import { computed, ref } from 'vue'
import AmountText from './AmountText.vue'
import { splitShares } from '@/lib/split'

export interface SplitPerson {
  memberId: string
  displayName: string
  personHue: number
  /** A whole number. Not a percentage — percentages are floats, and these decide money. */
  weight: number
}

/**
 * The draggable proportional split, and Tally's signature interaction.
 *
 * Ported with the arithmetic changed. Tally's version keeps float percentages and multiplies them
 * back out to amounts; this one keeps **integer weights** and asks the same largest-remainder
 * routine the server uses. The amounts under the bar are therefore what the item will actually be
 * split into, to the cent, before it is saved — which is the whole reason the client mints the
 * item's id.
 */
const props = withDefaults(
  defineProps<{
    people: SplitPerson[]
    totalMinor: number
    /** The item's salt. Comes from its id, so the preview matches the server exactly. */
    salt: bigint
    currencyCode?: string
    symbol?: string
    height?: number
  }>(),
  { currencyCode: 'AUD', symbol: '$', height: 56 },
)

const emit = defineEmits<{ 'update:people': [SplitPerson[]] }>()

const bar = ref<HTMLElement | null>(null)
const dragging = ref<number | null>(null)

const totalWeight = computed(() => props.people.reduce((sum, p) => sum + p.weight, 0) || 1)

/** What each person actually pays. The engine's answer, not an approximation of it. */
const amounts = computed(() =>
  splitShares({
    totalMinor: props.totalMinor,
    weights: props.people.map((p) => p.weight),
    salt: props.salt,
  }),
)

const percents = computed(() => props.people.map((p) => (p.weight / totalWeight.value) * 100))

const edges = computed(() => {
  let running = 0
  return percents.value.slice(0, -1).map((p) => (running += p))
})

function beginDrag(index: number, event: PointerEvent) {
  event.preventDefault()
  dragging.value = index
  ;(event.target as Element).setPointerCapture?.(event.pointerId)
}

function endDrag() {
  dragging.value = null
}

function onMove(event: PointerEvent) {
  const index = dragging.value
  if (index === null || !bar.value) return

  const box = bar.value.getBoundingClientRect()
  const fraction = Math.min(1, Math.max(0, (event.clientX - box.left) / box.width))

  // The drag is converted to whole weights immediately. Holding a float and rounding at save time
  // is how a bar that reads 33/33/34 turns into something else entirely once it lands.
  const pairWeight = props.people[index]!.weight + props.people[index + 1]!.weight
  const before = props.people.slice(0, index).reduce((sum, p) => sum + p.weight, 0)
  const target = Math.round(fraction * totalWeight.value) - before

  // Nobody on the bar can be dragged to nothing: a person at zero owes nothing, which is a
  // different statement from a small share, and it should be made by removing them.
  const left = Math.min(Math.max(target, 1), pairWeight - 1)

  emit(
    'update:people',
    props.people.map((person, i) =>
      i === index
        ? { ...person, weight: left }
        : i === index + 1
          ? { ...person, weight: pairWeight - left }
          : person,
    ),
  )
}
</script>

<template>
  <div class="split" @pointermove="onMove" @pointerup="endDrag" @pointercancel="endDrag">
    <div ref="bar" class="split__bar" :style="{ height: `${height}px` }">
      <div
        v-for="(person, i) in people"
        :key="person.memberId"
        class="split__slice"
        :class="{ 'split__slice--still': dragging === null }"
        :style="{
          width: `${percents[i]}%`,
          background: `var(--person-${((person.personHue - 1) % 8) + 1})`,
          color: person.personHue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
        }"
      >
        <span v-if="(percents[i] ?? 0) > 12" class="split__percent">{{ Math.round(percents[i]!) }}%</span>
      </div>

      <span
        v-for="(edge, i) in edges"
        :key="`edge-${i}`"
        class="split__handle-hit"
        :style="{ left: `calc(${edge}% - 13px)` }"
        role="separator"
        :aria-label="`Adjust the split between ${people[i]?.displayName} and ${people[i + 1]?.displayName}`"
        @pointerdown="beginDrag(i, $event)"
      >
        <span class="split__handle" :class="{ 'split__handle--held': dragging === i }" />
      </span>
    </div>

    <div class="split__legend">
      <span v-for="(person, i) in people" :key="person.memberId" class="split__entry">
        <span
          class="split__dot"
          :style="{ background: `var(--person-${((person.personHue - 1) % 8) + 1})` }"
        />
        <span class="split__name">{{ person.displayName }}</span>
        <AmountText
          :amount-minor="amounts[i] ?? 0"
          :currency-code="currencyCode"
          :symbol="symbol"
          size="sm"
        />
      </span>
    </div>
  </div>
</template>

<style scoped>
.split__bar {
  position: relative;
  display: flex;
  width: 100%;
  border: 2px solid var(--ink);
  border-radius: var(--radius-md);
  overflow: hidden;
  box-shadow: 0 3px 0 0 var(--ink);
  background: var(--bg-sunk);
  touch-action: none;
  user-select: none;
}

.split__slice {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

/* Only animates when nobody is dragging; easing a width under a moving thumb feels like lag. */
.split__slice--still {
  transition: width var(--dur-base) var(--ease-spring);
}

.split__percent {
  font-family: var(--font-money);
  font-size: 13px;
  font-weight: var(--weight-bold);
  white-space: nowrap;
}

.split__handle-hit {
  position: absolute;
  top: -2px;
  bottom: -2px;
  width: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: ew-resize;
  touch-action: none;
}

.split__handle {
  width: 14px;
  height: 76%;
  border-radius: var(--radius-pill);
  background: var(--surface-card);
  border: 2px solid var(--ink);
  transition: transform var(--dur-fast) var(--ease-spring);
}

.split__handle--held {
  transform: scaleY(1.12) scaleX(1.15);
  box-shadow: var(--lift-drag);
}

.split__legend {
  display: flex;
  gap: var(--space-4);
  margin-top: var(--space-3);
  flex-wrap: wrap;
}

.split__entry {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.split__dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-circle);
  border: 1.5px solid var(--ink);
}

.split__name {
  font-size: var(--text-caption);
  font-weight: var(--weight-semibold);
  color: var(--ink-2);
}
</style>
