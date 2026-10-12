<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useSessionStore } from '../session';
import { useHeaderCorrections } from './useHeaderCorrections';
import { kindLabels, statusLabels } from './header-contracts';
import type { HeaderKind } from './header-contracts';
const props = defineProps<{ actor: string; warehouse: string; order: string }>();
const session = useSessionStore(); const action = ref<HeaderKind | null>(null); const document = ref(''); const reason = ref('');
const patch = reactive({ orderNo: '', vehicle: '', container: '', remark: '' });
const { data, pending, busy, blocked, error, load, act, recover } = useHeaderCorrections(props);
const unresolved = computed(() => pending.value && !pending.value.receipt && !pending.value.rejection);
const canMake = computed(() => session.identity?.permissions.includes('outbound:correct'));
const canApprove = computed(() => session.identity?.permissions.includes('correction:approve'));
function open(kind: HeaderKind, id = ''): void {
  action.value = kind; document.value = id; reason.value = '';
  if (kind === 'CREATE' && data.value) for (const key of ['orderNo', 'vehicle', 'container', 'remark'] as const) patch[key] = data.value.header[key];
}
async function confirm(): Promise<void> {
  if (!action.value) return;
  await act(action.value, { document: document.value || undefined, reason: reason.value.trim(), headerPatch: action.value === 'CREATE' ? { ...patch } : undefined });
  if (pending.value?.receipt) action.value = null;
}
</script>
<template>
  <section aria-label="抬头更正与撤销补偿">
    <p>抬头更正由另一位主管批准。撤销由原审批人发起、原申请人确认；仅恢复实际改变的字段，后续其他字段与重量更正保留。</p>
    <el-alert v-if="error && !action" :title="error" type="error" :closable="false" />
    <el-alert v-if="unresolved" title="原操作结果尚未确认，原请求已保留。" type="warning" :closable="false">
      <el-button :loading="busy" @click="recover">核对或重试原请求</el-button>
    </el-alert>
    <el-alert v-if="pending?.receipt" :title="`${kindLabels[pending.receipt.commandType]}：${statusLabels[pending.receipt.businessDecision.status] ?? pending.receipt.businessDecision.status}`" type="success" :closable="false" />
    <el-alert v-if="pending?.rejection" :title="`原操作已拒绝：${pending.rejection.message}。原请求仍保留，请核对原因后重新操作。`" type="warning" :closable="false" />
    <template v-if="data">
      <el-descriptions title="本版本抬头" :column="2" border>
        <el-descriptions-item label="结果版本">{{ data.context.currentResultVersion }}</el-descriptions-item>
        <el-descriptions-item label="单号">{{ data.header.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="车号">{{ data.header.vehicle }}</el-descriptions-item>
        <el-descriptions-item label="柜号">{{ data.header.container }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ data.header.remark }}</el-descriptions-item>
      </el-descriptions>
      <el-button v-if="canMake" :disabled="busy || blocked || !!unresolved" @click="open('CREATE')">申请抬头更正</el-button>
      <el-button :disabled="busy || !!unresolved" @click="load()">刷新抬头与更正记录</el-button>
      <el-empty v-if="!data.items.length" description="暂无抬头更正记录" />
      <article v-for="item in data.items" :key="item.context.documentId" :data-header-document="item.context.documentId">
        <h3>{{ statusLabels[item.context.status] }} · 原结果版本 {{ item.context.expectedResultVersion }}</h3>
        <p>原因：{{ item.reason }}</p><p>更正单：{{ item.context.documentId }}</p>
        <dl><template v-for="(value, key) in item.headerPatch" :key="key"><dt>{{ { orderNo: '单号', vehicle: '车号', container: '柜号', remark: '备注' }[key] }}</dt><dd>{{ value || '空' }}</dd></template></dl>
        <el-button v-if="canApprove && item.context.status === 'SUBMITTED' && item.context.requestedBy !== actor" :disabled="busy || blocked || !!unresolved" @click="open('APPROVE', item.context.documentId)">审批抬头更正</el-button>
        <el-button v-if="canApprove && item.context.status === 'EFFECTIVE' && item.context.approvedBy === actor" :disabled="busy || blocked || !!unresolved" @click="open('WITHDRAW', item.context.documentId)">发起撤销补偿</el-button>
        <el-button v-if="canMake && item.context.status === 'WITHDRAW_PENDING' && item.context.requestedBy === actor" :disabled="busy || blocked || !!unresolved" @click="open('WITHDRAW_CONFIRM', item.context.documentId)">确认撤销补偿</el-button>
      </article>
      <el-button v-if="data.nextCursor" :disabled="busy || !!unresolved" @click="load(true)">更早的抬头更正</el-button>
    </template>
    <el-dialog :model-value="!!action" :title="action ? kindLabels[action] : ''" width="560" :show-close="!busy" :close-on-click-modal="false" :close-on-press-escape="!busy" @close="action = null">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <p v-if="action !== 'CREATE'">请核对更正单 {{ document }} 与当前字段；同字段已有后续更正时，撤销补偿会被拒绝。</p>
      <el-form v-if="action === 'CREATE'" label-position="top">
        <el-form-item label="单号"><el-input v-model="patch.orderNo" aria-label="更正单号" maxlength="100" :disabled="busy || !!unresolved" /></el-form-item>
        <el-form-item label="车号"><el-input v-model="patch.vehicle" aria-label="更正车号" maxlength="200" :disabled="busy || !!unresolved" /></el-form-item>
        <el-form-item label="柜号"><el-input v-model="patch.container" aria-label="更正柜号" maxlength="200" :disabled="busy || !!unresolved" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="patch.remark" aria-label="更正备注" type="textarea" maxlength="1000" :disabled="busy || !!unresolved" /></el-form-item>
      </el-form>
      <el-input v-model="reason" aria-label="抬头更正操作原因" placeholder="填写本次申请、审批或撤销原因" type="textarea" maxlength="500" :disabled="busy || !!unresolved" />
      <template #footer><el-button :disabled="busy" @click="action = null">返回核对</el-button>
        <el-button type="primary" :loading="busy" :disabled="!!unresolved || !reason.trim()" @click="confirm">{{ action === 'WITHDRAW_CONFIRM' ? kindLabels[action] : `确认${action ? kindLabels[action] : ''}` }}</el-button></template>
    </el-dialog>
  </section>
</template>
