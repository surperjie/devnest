<script setup>
import { ref } from "vue";

const greeting = ref("Hello, DevNest");
</script>

<template>
  <main class="app">
    <h1>{{ greeting }}</h1>
    <p>DevNest desktop UI (Tauri 2 + Vue 3)</p>
  </main>
</template>

<style scoped>
.app {
  font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue",
    Arial, "PingFang SC", "Microsoft YaHei", sans-serif;
  color: #1f2937;
  padding: 2rem;
}
</style>
