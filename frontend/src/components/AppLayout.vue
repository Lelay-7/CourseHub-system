<template>
  <AppShell>
    <slot>
      <router-view v-slot="{ Component, route }">
        <transition name="fade-slide" mode="out-in">
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </transition>
      </router-view>
    </slot>
  </AppShell>
</template>

<script setup>
import { computed } from 'vue'
import AppShell from './AppShell.vue'

const cachedViews = computed(() => [
  'CourseListView',
  'MaterialListView',
  'ContactListView',
  'MallListView',
  'SquareView'
])
</script>
