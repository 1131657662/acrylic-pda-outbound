<script setup lang="ts">
import { onMounted, ref } from 'vue';
import QRCode from 'qrcode';
const address = `${location.origin}/pda-download`;
const code = ref(''); const feedback = ref('');
onMounted(async () => {
  if (location.protocol !== 'https:') return;
  try { code.value = await QRCode.toDataURL(address, { errorCorrectionLevel: 'M', margin: 4, width: 232 }); }
  catch { feedback.value = '二维码暂时无法生成，请手动输入下方地址。'; }
});
async function copy(): Promise<void> {
  try { await navigator.clipboard.writeText(address); feedback.value = '下载页地址已复制。'; }
  catch { feedback.value = '无法自动复制，请选中下方完整地址复制。'; }
}
</script>
<template>
  <section class="download-section download-address">
    <h2>在 PDA 上打开此页面</h2>
    <p>用 PDA 浏览器输入下方地址；也可使用能打开网址的扫码工具扫描二维码。</p>
    <img v-if="code" :src="code" alt="PDA 应用固定下载页二维码" width="232" height="232" />
    <a class="mono download-url" :href="address">{{ address }}</a>
    <el-button @click="copy">复制下载页地址</el-button>
    <p v-if="feedback" role="status">{{ feedback }}</p>
  </section>
</template>
