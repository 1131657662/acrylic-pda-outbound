<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from '../session';
import { navigation } from '../navigation';
defineProps<{ collapsed?: boolean; busy?: boolean }>();
defineEmits<{ navigate: [] }>();
const session = useSessionStore(); const route = useRoute();
const groups = computed(() => navigation.map(group => ({ ...group,
  items: group.items.filter(item => session.identity?.permissions.includes(item.permission)),
})).filter(group => group.items.length));
</script>
<template>
  <nav class="navigation" aria-label="业务导航">
    <div v-for="group in groups" :key="group.group">
      <p class="nav-caption">{{ group.group }}</p>
      <el-tooltip v-for="item in group.items" :key="item.path" :content="item.label" placement="right" :disabled="!collapsed">
        <router-link :to="item.path" class="nav-item" :class="{ 'is-active': route.path.startsWith(item.path) }"
          :aria-current="route.path.startsWith(item.path) ? 'page' : undefined" :aria-label="item.label" :aria-disabled="busy"
          @click="busy ? $event.preventDefault() : $emit('navigate')">
          <el-icon><component :is="item.icon" /></el-icon><span class="nav-label">{{ item.label }}</span>
        </router-link>
      </el-tooltip>
    </div>
  </nav>
</template>
