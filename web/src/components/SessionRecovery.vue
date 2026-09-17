<script setup lang="ts">
import { ref } from 'vue';
import { connection } from '../connection';
import { useSessionStore } from '../session';
import { errorText } from '../api';
const session = useSessionStore(); const password = ref(''); const error = ref(''); const busy = ref(false);
async function login(): Promise<void> {
  if (busy.value || !session.identity) return;
  busy.value = true; error.value = '';
  try { await session.login(session.identity.username, password.value); }
  catch (cause) { error.value = errorText(cause); }
  finally { busy.value = false; password.value = ''; }
}
</script>
<template>
  <el-dialog :model-value="connection.state === 'expired' && !!session.identity" title="重新登录" width="440" :show-close="false" :close-on-click-modal="false" :close-on-press-escape="false">
    <p>登录已失效。当前页面与原操作内容保留，登录后请核对结果。</p>
    <form class="recovery-form" @submit.prevent="login">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <label>账号<el-input :model-value="session.identity?.username" disabled autocomplete="username" /></label>
      <label>密码<el-input v-model="password" aria-label="重新登录密码" type="password" autocomplete="current-password" show-password required /></label>
      <el-button native-type="submit" type="primary" :loading="busy">重新登录</el-button>
    </form>
  </el-dialog>
</template>
