<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { errorText } from '../api';
import { useSessionStore } from '../session';

const session = useSessionStore();
const router = useRouter();
const username = ref('');
const password = ref('');
const busy = ref(false);
const error = ref('');
async function submit(): Promise<void> {
  if (busy.value) return;
  busy.value = true; error.value = '';
  try { await session.login(username.value, password.value); await router.replace('/inventory/imports'); }
  catch (cause) { error.value = errorText(cause); }
  finally { busy.value = false; password.value = ''; }
}
</script>

<template>
  <main class="login-layout">
    <section class="login-brand">
      <div class="brand-mark">纤</div><p class="eyebrow">ACRYLIC WAREHOUSE</p>
      <h1>每一包，<br />都有据可查。</h1>
      <p>腈纶仓储 · 出库管理</p><div class="brand-rule"></div>
      <p class="brand-caption">库存清晰 · 作业有序 · 记录完整</p>
    </section>
    <section class="login-form-panel">
      <form class="login-form" @submit.prevent="submit">
        <p class="eyebrow">管理工作台</p><h2>登录仓储系统</h2><p class="muted">使用管理员分配的账号继续。</p>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <label for="username">账号</label>
        <el-input id="username" v-model="username" autocomplete="username" size="large" maxlength="64" required />
        <label for="password">密码</label>
        <el-input id="password" v-model="password" type="password" autocomplete="current-password" size="large" show-password required />
        <el-button native-type="submit" type="primary" size="large" :loading="busy" class="login-submit">登录</el-button>
      </form>
    </section>
  </main>
</template>
