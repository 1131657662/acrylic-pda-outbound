<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { loginDestination } from '../router';
import { errorText } from '../api';
import { useSessionStore } from '../session';

const session = useSessionStore();
const router = useRouter();
const route = useRoute();
const username = ref('');
const password = ref('');
const busy = ref(false);
const error = ref('');
const environment = import.meta.env.VITE_ENVIRONMENT_NAME?.trim() || (import.meta.env.DEV ? '开发环境' : '正式环境');
async function submit(): Promise<void> {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try { await session.login(username.value, password.value); await router.replace(loginDestination(route.query.redirect, session.identity?.permissions ?? [])); }
  catch (cause) { error.value = errorText(cause); }
  finally { busy.value = false; password.value = ''; }
}
</script>

<template>
  <main class="login-layout">
      <form class="login-form" @submit.prevent="submit">
        <div class="brand-mark">纤</div><h1>腈纶出库</h1><p class="muted">登录管理工作台</p>
        <el-tag class="login-environment" type="info">当前环境：{{ environment }}</el-tag>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <label for="username">账号</label>
        <el-input id="username" v-model="username" autocomplete="username" size="large" maxlength="64" required :disabled="busy" />
        <label for="password">密码</label>
        <el-input id="password" v-model="password" type="password" autocomplete="current-password" size="large" show-password required :disabled="busy" />
        <el-button native-type="submit" type="primary" size="large" :loading="busy" class="login-submit">登录</el-button>
      </form>
  </main>
</template>
