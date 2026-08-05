<script setup lang="ts">
import AmountText from './components/AmountText.vue'
import AvatarStack from './components/AvatarStack.vue'
import PersonAvatar from './components/PersonAvatar.vue'
import TallyBadge from './components/TallyBadge.vue'
import TallyButton from './components/TallyButton.vue'
import TallyCard from './components/TallyCard.vue'

/**
 * A gallery of what has been ported so far, not a screen.
 *
 * The real screens are build order step 9; this exists so the components can be looked at in a
 * browser while they are being built, and it will be replaced by the router when the screens land.
 */
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
