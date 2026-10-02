// Isolated component fixture: synthetic identity and API; never included in the production entry point.
import { createApp, h } from 'vue';
import { createPinia } from 'pinia';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import TemporaryCommandAction from '../src/temporary-review/TemporaryCommandAction.vue';
import { useSessionStore } from '../src/session';

const query = new URLSearchParams(location.search);
const pinia = createPinia(); const session = useSessionStore(pinia);
session.identity = { id: query.get('user'), username: 'synthetic-reviewer', displayName: '隔离复验',
  permissions: ['temporary_order:confirm', 'temporary_order:link'], warehouses: [] };
session.warehouseId = query.get('warehouse');
createApp({ render: () => h(TemporaryCommandAction, {
  kind: query.get('kind'), submissionId: query.get('submission'), warehouseId: query.get('warehouse'),
  candidateContextSha256: 'c'.repeat(64), available: true, unavailableReason: '',
  label: '隔离命令', hint: '浏览器边界复验', reasonRequired: query.get('kind') === 'LINK',
}) }).use(pinia).use(ElementPlus).mount('#app');
