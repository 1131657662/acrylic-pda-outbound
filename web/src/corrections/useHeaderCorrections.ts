import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ApiError, currentRecoveryEpoch, errorText, getRaw, postRaw } from '../api';
import { useSessionStore } from '../session';
import { commandSchema, headersSchema, preparationSchema, receiptSchema } from './header-contracts';
import type { HeaderData, HeaderKind, HeaderCommand } from './header-contracts';
import { canonicalHeaderCommand, loadHeaderPending, saveHeaderPending, verifyHeaderResponse } from './header-pending';
import type { HeaderOwner, HeaderPending } from './header-pending';

export function useHeaderCorrections(owner: HeaderOwner) {
  const session = useSessionStore(); const data = ref<HeaderData | null>(null);
  const pending = ref<HeaderPending | null>(null); const error = ref(''); const busy = ref(false); const blocked = ref(false);
  let alive = true;
  function assertCurrent(): void {
    if (!alive || session.identity?.id !== owner.actor || session.warehouseId !== owner.warehouse)
      throw new Error('身份或仓库已变化，原请求未继续发送。');
  }
  async function load(older = false): Promise<void> {
    try {
      assertCurrent(); const saved = loadHeaderPending(owner);
      if (saved?.receipt) await verifyHeaderResponse(saved, saved.receipt);
      pending.value = saved;
      const review = !session.identity?.permissions.includes('outbound:correct');
      const cursor = older && data.value?.nextCursor ? `&before=${data.value.nextCursor}` : '';
      const loaded = await getRaw(`/corrections/orders/${owner.order}/headers?warehouseId=${owner.warehouse}&review=${review}${cursor}`, headersSchema);
      assertCurrent();
      if (loaded.context.orderId !== owner.order || loaded.context.warehouseId !== owner.warehouse) throw new Error('抬头订单身份不匹配');
      data.value = older && data.value ? { ...loaded, items: [...data.value.items, ...loaded.items] } : loaded;
      error.value = ''; blocked.value = false;
    } catch (cause) {
      if (alive) { error.value = cause instanceof ApiError ? errorText(cause) : '抬头更正资料或原请求不可核验，请保留资料后重新读取。'; blocked.value = true; }
    }
  }
  async function prepare(kind: HeaderKind, input: { document?: string; reason: string; headerPatch?: HeaderCommand['headerPatch'] }): Promise<HeaderPending> {
    const path = kind === 'CREATE' ? `/orders/${owner.order}/context`
      : `/${input.document}/context`;
    const facts = await getRaw(`/corrections${path}?warehouseId=${owner.warehouse}&commandType=${kind}`, preparationSchema);
    assertCurrent();
    if (facts.context.orderId !== owner.order || facts.context.warehouseId !== owner.warehouse
        || kind !== 'CREATE' && facts.context.documentId !== input.document) throw new Error('更正上下文身份不匹配');
    const command: HeaderCommand = { commandVersion: 1, commandId: crypto.randomUUID(), commandType: kind,
      warehouseId: owner.warehouse, transportRecoveryEpoch: currentRecoveryEpoch(), reason: input.reason, expected: facts.expected,
      ...(kind === 'CREATE' ? { orderId: owner.order, type: 'HEADER_CORRECTION' as const, headerPatch: input.headerPatch }
        : { documentId: input.document }) };
    const saved = { ...owner, body: canonicalHeaderCommand(command), receipt: null, rejection: null };
    saveHeaderPending(saved); return saved;
  }
  async function transmit(original: HeaderPending): Promise<void> {
    assertCurrent(); pending.value = original;
    if (original.receipt) { await verifyHeaderResponse(original, original.receipt); return; }
    const command = commandSchema.parse(JSON.parse(original.body));
    const receipt = await send(original, command.transportRecoveryEpoch);
    assertCurrent(); await verifyHeaderResponse(original, receipt);
    saveHeaderPending({ ...original, receipt }); pending.value = { ...original, receipt };
    await load();
  }
  async function send(original: HeaderPending, epoch: string) {
    try { return await postRaw('/corrections', { json: original.body, recoveryEpoch: epoch, assertCurrent }, receiptSchema); }
    catch (cause) {
      if (alive && cause instanceof ApiError && [400, 403, 409].includes(cause.status)) {
        const rejected = { ...original, rejection: { status: cause.status, message: cause.message } };
        saveHeaderPending(rejected); pending.value = rejected;
      }
      throw cause;
    }
  }
  async function act(kind: HeaderKind, input: { document?: string; reason: string; headerPatch?: HeaderCommand['headerPatch'] }): Promise<void> {
    if (busy.value || blocked.value) return;
    busy.value = true; error.value = '';
    try {
      if (!navigator.locks) throw new Error('浏览器不能保护跨页原请求');
      await navigator.locks.request(`header-correction:${owner.actor}:${owner.warehouse}:${owner.order}`, async () => {
        assertCurrent(); const saved = loadHeaderPending(owner);
        await transmit(saved && !saved.receipt && !saved.rejection ? saved : await prepare(kind, input));
      });
    } catch (cause) {
      if (alive) error.value = cause instanceof ApiError ? errorText(cause)
        : pending.value && !pending.value.receipt && !pending.value.rejection
          ? '操作结果未确认，原请求已保留，请核对或重试原请求。'
          : '原请求未能完整保存或准备失败，本次操作未发送。请保留资料后重新核对。';
    }
    finally { if (alive) busy.value = false; }
  }
  async function recover(): Promise<void> {
    if (busy.value || !pending.value || pending.value.receipt) return;
    busy.value = true; error.value = '';
    try {
      if (!navigator.locks) throw new Error('浏览器不能保护跨页原请求');
      await navigator.locks.request(`header-correction:${owner.actor}:${owner.warehouse}:${owner.order}`, async () => {
        assertCurrent(); const original = loadHeaderPending(owner); if (!original) throw new Error('原请求缺失');
        const command = commandSchema.parse(JSON.parse(original.body));
        try {
          const receipt = await getRaw(`/corrections/commands/${command.commandId}?warehouseId=${owner.warehouse}&commandType=${command.commandType}`, receiptSchema);
          assertCurrent(); await verifyHeaderResponse(original, receipt); saveHeaderPending({ ...original, receipt }); await load();
        } catch (cause) { if (!(cause instanceof ApiError) || cause.status !== 404) throw cause; await transmit(original); }
      });
    } catch (cause) { if (alive) error.value = cause instanceof ApiError ? errorText(cause) : '原结果仍不可确认，请保留原请求。'; }
    finally { if (alive) busy.value = false; }
  }
  onMounted(() => { void load(); }); onBeforeUnmount(() => { alive = false; });
  return { data, pending, busy, blocked, error, load, act, recover };
}
