<script setup lang="ts">defineProps<{ title: string; stage: string }>()</script>
<template><section class="card"><h2>{{ title }}</h2><p class="muted">该模块将在 {{ stage }} 阶段实现。</p></section></template>
