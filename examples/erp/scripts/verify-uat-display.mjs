import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
const base = new URL('../public/js/', import.meta.url);
const stamp = Date.UTC(2026, 9, 16, 12);
const fixtures = {
  '/api/erp/inventory/transactions': [{id: 1, createdAt: stamp, quantity: -3}],
  '/api/erp/finance/journals': [{id: 1, postingDate: stamp}],
  '/api/erp/finance/gl': [{id: 1, postingDate: stamp, journalNumber: 'JE-UAT'}],
  '/api/erp/finance/incoming-payments': [{id: 1, paymentDate: stamp}],
  '/api/erp/finance/outgoing-payments': [{id: 1, paymentDate: stamp}]
};
const context = {window: {}, fetch: async url => ({ok: true, status: 200, text: async () => JSON.stringify(fixtures[url])})};
vm.createContext(context);
for (const file of ['data/provider.js', 'registry.js']) {
  vm.runInContext(fs.readFileSync(new URL(file, base), 'utf8'), context, {filename: fileURLToPath(new URL(file, base))});
}
for (const name of ['inventoryTransactions', 'journalEntries', 'glLines', 'incomingPayments', 'outgoingPayments']) {
  const result = await context.window.JnData.active.list(name, {});
  assert.equal(result.rows[0].date, '2026-10-16', name);
  if (name === 'glLines') assert.equal(result.rows[0].journal, 'JE-UAT');
}
const value = context.window.JnRegistry.getRaw('inv.balance').columns.find(c => c.key === 'value');
assert.equal(value.type, 'money');
console.log('UAT_DISPLAY_PASS: five business dates, GL reference, inventory money column');
