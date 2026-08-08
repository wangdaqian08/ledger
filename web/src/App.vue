<script setup lang="ts">
/**
 * The frame and nothing else: a phone-width column on the app background, with the router
 * deciding what fills it. This replaced the component gallery when the screens landed
 * (build order step 9).
 */
</script>

<template>
  <div class="frame">
    <RouterView />
  </div>
</template>

<style scoped>
.frame {
  max-width: 430px;
  min-height: 100dvh;
  margin: 0 auto;
  background: var(--bg-app);
}
</style>
