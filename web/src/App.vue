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
import AppBar from './components/AppBar.vue'
import BalanceRow from './components/BalanceRow.vue'
import CategoryPicker from './components/CategoryPicker.vue'
import EmptyState from './components/EmptyState.vue'
import ExpenseRow from './components/ExpenseRow.vue'
import GroupCard from './components/GroupCard.vue'
import ProgressBar from './components/ProgressBar.vue'
import SettledBanner from './components/SettledBanner.vue'
import SheetPanel from './components/SheetPanel.vue'
import TabBar from './components/TabBar.vue'
import TallyKeypad from './components/TallyKeypad.vue'
import TallyStepper from './components/TallyStepper.vue'
import TextField from './components/TextField.vue'

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
const sheetOpen = ref(false)
const tab = ref('groups')
const weight = ref(2)
const tripName = ref('')
const chosenCategory = ref<string | null>('cat-stay')

const categories = [
  { id: 'cat-food', key: 'food', nameEn: 'Food', nameZh: '餐饮', icon: 'utensils', hue: 2, builtIn: true },
  { id: 'cat-stay', key: 'stay', nameEn: 'Stay', nameZh: '住宿', icon: 'bed-double', hue: 7, builtIn: true },
  { id: 'cat-fun', key: 'fun', nameEn: 'Fun', nameZh: '娱乐', icon: 'ticket', hue: 8, builtIn: true },
]

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

    <TallyCard>
      <h2>A trip in the list</h2>
      <GroupCard name="Hokkaido" icon="plane" :hue="6" :members="people" :your-net-minor="3410" />
      <GroupCard name="Flat" icon="house" :hue="4" :members="people.slice(0, 2)" :your-net-minor="0" />
    </TallyCard>

    <TallyCard>
      <h2>Expenses and balances</h2>
      <ExpenseRow
        title="Hotel deposit"
        category-key="stay"
        paid-by="Bob"
        spent-on="1 Aug"
        :your-share-minor="-14286"
      />
      <ExpenseRow
        title="Dinner"
        category-key="food"
        paid-by="Mei"
        spent-on="2 Aug"
        :your-share-minor="0"
        all-square
      />
      <BalanceRow display-name="Mei Lin" :person-hue="3" :owed-minor="-3910" />
      <BalanceRow display-name="Sam Patel" :person-hue="4" :owed-minor="4230" />
      <BalanceRow display-name="Jack Bell" :person-hue="5" :owed-minor="0" />
    </TallyCard>

    <TallyCard>
      <h2>How much of a bill is back</h2>
      <ProgressBar :covered-minor="6800" :of-minor="10000" label="Paid back" />
    </TallyCard>

    <TallyCard>
      <h2>Choosing and typing</h2>
      <CategoryPicker v-model="chosenCategory" :categories="categories" />
      <p>
        <TextField
          v-model="tripName"
          label="Trip name"
          placeholder="Hokkaido"
          hint="Anything the group will recognise"
        />
      </p>
      <p>Weight <TallyStepper v-model="weight" :min="1" :max="9" /></p>
    </TallyCard>

    <TallyCard>
      <h2>Sheets and navigation</h2>
      <AppBar title="Hokkaido" back action="share-2" />
      <p><TallyButton @click="sheetOpen = true">Open a sheet</TallyButton></p>
      <TabBar
        v-model="tab"
        :tabs="[
          { id: 'groups', label: 'Groups', icon: 'users' },
          { id: 'activity', label: 'Activity', icon: 'clock' },
          { id: 'you', label: 'You', icon: 'circle-user' },
        ]"
      />
    </TallyCard>

    <TallyCard>
      <h2>Nothing here yet</h2>
      <EmptyState
        title="No expenses"
        body="Add the first one and everybody's share appears."
        action="Add an expense"
      />
    </TallyCard>

    <SettledBanner sub="Every row has been confirmed." />

    <SheetPanel :open="sheetOpen" title="Add an expense" @close="sheetOpen = false">
      <AmountInput v-model="amount" />
      <TallyKeypad style="margin-top: 16px" />
    </SheetPanel>

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
