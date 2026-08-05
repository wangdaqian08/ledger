<script setup lang="ts">
import AmountText from './components/AmountText.vue'
import AvatarStack from './components/AvatarStack.vue'
import PersonAvatar from './components/PersonAvatar.vue'
import TallyBadge from './components/TallyBadge.vue'
import TallyButton from './components/TallyButton.vue'
import TallyCard from './components/TallyCard.vue'
import SplitBar from './components/SplitBar.vue'
import AmountInput from './components/AmountInput.vue'
import PersonToggleRow from './components/PersonToggleRow.vue'
import { ref } from 'vue'
import { saltFor, splitShares } from './lib/split'

/**
 * A gallery of what has been ported so far, not a screen.
 *
 * The real screens are build order step 9; this exists so the components can be looked at in a
 * browser while they are being built, and it will be replaced by the router when the screens land.
 */
const amount = ref(20000)

// A fixed id so the gallery is stable to look at; a real sheet mints one with newItemId().
const salt = saltFor('01234567-89ab-cdef-0000-000000000000')

const split = ref([
  { memberId: '1', displayName: 'Bob', personHue: 1, weight: 2 },
  { memberId: '2', displayName: 'Alice', personHue: 2, weight: 1 },
  { memberId: '3', displayName: 'Mei', personHue: 3, weight: 1 },
])

const onList = ref<Record<string, boolean>>({ '1': true, '2': true, '3': false })

const previewShares = () =>
  splitShares({
    totalMinor: amount.value,
    weights: split.value.map((p) => p.weight),
    salt,
  })

const people = [
  { id: '1', displayName: 'Bob Chen', personHue: 1 },
  { id: '2', displayName: 'Alice Wu', personHue: 2 },
  { id: '3', displayName: 'Mei Lin', personHue: 3 },
  { id: '4', displayName: 'Sam Patel', personHue: 4 },
  { id: '5', displayName: 'Jack Bell', personHue: 5 },
]
</script>

<template>
  <main class="gallery">
    <h1>Ledger components</h1>

    <TallyCard>
      <h2>Money</h2>
      <p><AmountText :amount-minor="123405" size="hero" tone="owed" show-sign /></p>
      <p><AmountText :amount-minor="-6594" size="lg" tone="owe" show-sign /></p>
      <p><AmountText :amount-minor="0" tone="settled" /></p>
      <p><AmountText :amount-minor="333400" currency-code="JPY" symbol="¥" /></p>
    </TallyCard>

    <TallyCard>
      <h2>People</h2>
      <p>
        <PersonAvatar v-for="p in people" :key="p.id" :name="p.displayName" :hue="p.personHue" />
      </p>
      <p><AvatarStack :people="people" :max="3" /></p>
    </TallyCard>

    <TallyCard>
      <h2>Buttons</h2>
      <p>
        <TallyButton>Pay</TallyButton>
        <TallyButton variant="secondary">Remind</TallyButton>
        <TallyButton variant="ghost">Done for now</TallyButton>
        <TallyButton variant="danger">Delete</TallyButton>
      </p>
    </TallyCard>

    <TallyCard>
      <h2>Badges</h2>
      <p>
        <TallyBadge tone="settled">All square</TallyBadge>
        <TallyBadge tone="pending">Waiting for Mei</TallyBadge>
        <TallyBadge tone="owe">You owe</TallyBadge>
      </p>
    </TallyCard>

    <TallyCard>
      <h2>Entering an amount</h2>
      <p><AmountInput v-model="amount" /></p>
      <p>Stored as {{ amount }} minor units — never a float.</p>
    </TallyCard>

    <TallyCard>
      <h2>Dragging a split</h2>
      <SplitBar v-model:people="split" :total-minor="amount" :salt="salt" />
      <p>These are the engine's own figures, to the cent, before anything is saved.</p>
    </TallyCard>

    <TallyCard>
      <h2>Who is on it</h2>
      <p v-for="(person, i) in split" :key="person.memberId" style="display: block">
        <PersonToggleRow
          :selected="onList[person.memberId] ?? false"
          :display-name="person.displayName"
          :person-hue="person.personHue"
          :share-minor="previewShares()[i] ?? 0"
          @update:selected="onList[person.memberId] = $event"
        />
      </p>
    </TallyCard>

    <TallyCard sunk>
      <h2>A settled card sinks</h2>
      <p>Greyed and lowered, but never hidden.</p>
    </TallyCard>
  </main>
</template>

<style scoped>
.gallery {
  max-width: 640px;
  margin: 0 auto;
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

p {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}
</style>
