<script setup lang="ts">
import { computed } from 'vue';
import DownloadAddress from './DownloadAddress.vue';
import DownloadStatus from './DownloadStatus.vue';
import { useDownload } from './useDownload';
import './download.css';
const { current, problem, phase, notice, busy, refresh, download } = useDownload();
const release = computed(() => current.value?.release);
const environment = import.meta.env.VITE_ENVIRONMENT_NAME?.trim() || '当前站点';
const androidVersions: Record<number, string> = { 28: '9', 29: '10', 30: '11', 31: '12', 32: '12L', 33: '13', 34: '14', 35: '15', 36: '16' };
</script>
<template>
  <main class="download-page">
    <header class="download-header">
      <p class="muted">纤序 · {{ current?.config.environmentName || environment }}</p>
      <h1>下载 PDA 应用</h1>
      <p>供新 PDA 首次安装，安装后需要短码登记和人员登录。</p>
      <el-tag v-if="current" disable-transitions :type="current.config.channel === 'staging' ? 'warning' : 'info'">
        {{ current.config.channel === 'staging' ? '测试版 · 仅用于测试环境' : '正式版' }}
      </el-tag>
    </header>
    <section class="download-section" :aria-busy="busy">
      <p v-if="phase === 'loading'" role="status">正在核对当前发布…</p>
      <DownloadStatus v-if="problem" :problem="problem" @retry="refresh" />
      <template v-if="release">
        <h2>当前可安装版本</h2>
        <dl class="download-facts">
          <div><dt>版本</dt><dd>{{ release.versionName }}（{{ release.versionCode }}）</dd></div>
          <div><dt>文件大小</dt><dd>{{ (release.sizeBytes / 1024 / 1024).toFixed(1) }} MB</dd></div>
          <div><dt>发布时间</dt><dd>{{ new Date(release.publishedAt).toLocaleString('zh-CN', { hour12: false }) }}</dd></div>
          <div><dt>系统要求</dt><dd>Android {{ androidVersions[release.minAndroidApi] || `API ${release.minAndroidApi}` }} 及以上</dd></div>
        </dl>
        <p class="download-notes">{{ release.releaseNotes }}</p>
        <div class="download-actions">
          <el-button type="primary" size="large" :loading="phase === 'downloading'" :disabled="busy" @click="download">
            {{ phase === 'downloading' ? '下载并校验中…' : '下载安装包' }}
          </el-button>
          <el-button :disabled="busy" @click="refresh">重新检查版本</el-button>
        </div>
        <details class="file-proof"><summary>安装包核对信息</summary>
          <p>包名：{{ release.packageName }}</p><p>SHA-256：{{ release.sha256 }}</p>
        </details>
      </template>
      <p v-if="notice" class="download-notice" role="status">{{ notice }}</p>
    </section>
    <section class="download-section">
      <h2>首次安装</h2>
      <ol class="download-steps">
        <li>下载 APK，在浏览器的下载记录中打开安装包。</li>
        <li>在 Android 系统安装器确认安装。如提示来源未允许，请按系统提示为当前浏览器授权后继续。</li>
        <li>打开“纤序作业”，请管理员在设备管理页核对短码完成登记。</li>
        <li>使用已授权的人员账号登录，再到设置中检查应用更新。</li>
      </ol>
      <p>下载完成只表示安装包已保存，是否安装成功以系统安装器和 App 实际状态为准。</p>
    </section>
    <section class="download-section">
      <h2>已有设备升级</h2>
      <p>优先使用 App 设置中的“检查应用更新”。旧版没有此入口时，请联系管理员核对覆盖接入版本。</p>
      <p>网页无法判断已装版本、签名及作业状态。请保留原应用和资料，不要卸载、清空数据或重新登记。</p>
    </section>
    <DownloadAddress />
    <footer class="download-section"><router-link to="/login">返回工作台登录</router-link><p class="muted">从设备管理打开时，切回原标签页即可继续原列表。</p></footer>
  </main>
</template>
