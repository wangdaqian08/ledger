<script setup lang="ts">
import TallyIcon from './TallyIcon.vue'

/**
 * The moment a trip comes out square.
 *
 * Never a button and never a claim the app makes on its own behalf: it appears because every row
 * has been approved by the person owed (§7a). It celebrates a derived state, and if it is showing
 * when somebody is still owed money, the bug is upstream of here.
 */
withDefaults(defineProps<{ message?: string; sub?: string }>(), {
  message: "You're all square",
  sub: undefined,
})
</script>

<template>
  <div class="banner" role="status">
    <span class="banner__disc"><TallyIcon name="party-popper" :size="24" /></span>
    <span class="banner__text">
      <strong class="banner__message">{{ message }}</strong>
      <span v-if="sub" class="banner__sub">{{ sub }}</span>
    </span>
  </div>
</template>

<style scoped>
.banner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--mint);
  color: var(--text-on-accent);
  border: 2px solid var(--ink);
  border-radius: var(--radius-lg);
  box-shadow: 0 4px 0 0 var(--ink);
  animation: settle-pop var(--dur-base) var(--ease-spring);
}

@keyframes settle-pop {
  from {
    transform: scale(0.96);
    opacity: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .banner {
    animation: none;
  }
}

.banner__disc {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  background: var(--paper);
  color: var(--mint-press);
  border: 2px solid var(--ink);
  border-radius: var(--radius-circle);
}

.banner__text {
  display: flex;
  flex-direction: column;
}

.banner__message {
  font-size: var(--text-heading-sm);
  font-weight: var(--weight-black);
}

.banner__sub {
  font-size: var(--text-caption);
  opacity: 0.9;
  margin-top: 2px;
}
</style>
