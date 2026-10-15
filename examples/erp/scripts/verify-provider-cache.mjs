#!/usr/bin/env node
/**
 * Controllable-response regression for provider.js cache / error handling.
 * Author: 张晓达
 */
import fs from "fs";
import path from "path";
import vm from "vm";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const providerPath = path.resolve(__dirname, "../public/js/data/provider.js");

let networkCalls = 0;
let scripted = [];

function nextResponse() {
  if (!scripted.length) throw new Error("no scripted response left");
  return scripted.shift();
}

globalThis.fetch = async function () {
  networkCalls++;
  const r = nextResponse();
  return {
    ok: r.status >= 200 && r.status < 300,
    status: r.status,
    text: async () => r.body
  };
};

const sandbox = {
  console,
  fetch: globalThis.fetch,
  sessionStorage: {
    _m: {},
    getItem(k) { return this._m[k] ?? null; },
    setItem(k, v) { this._m[k] = String(v); },
    removeItem(k) { delete this._m[k]; }
  },
  document: {
    dispatchEvent() {},
    createEvent() { return { initEvent() {} }; }
  },
  CustomEvent: function () {},
  window: {},
  Object,
  Array,
  Promise,
  JSON,
  Error,
  Math,
  Number,
  String,
  Date,
  isNaN,
  isFinite,
  encodeURIComponent,
  setTimeout,
  clearTimeout
};
sandbox.global = sandbox;
sandbox.window = sandbox;

const code = fs.readFileSync(providerPath, "utf8");
vm.runInNewContext(code + "\nthis.__JnData = JnData;\n", sandbox, { filename: "provider.js" });
const Provider = sandbox.__JnData && sandbox.__JnData.RpcDataProvider;
if (!Provider) {
  throw new Error("RpcDataProvider not exported; JnData=" + typeof sandbox.JnData);
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

async function caseInvalidJson() {
  networkCalls = 0;
  scripted = [
    { status: 200, body: '[{"id":1,"sku":"A"}\x07]' }, // control char → invalid JSON
    { status: 200, body: '[{"id":1,"sku":"A","name":"Widget","unitPriceCents":100}]' }
  ];
  const p = new Provider("");
  let first;
  try {
    first = await p.list("items", { page: 1, pageSize: 8 });
    throw new Error("expected throw on invalid JSON");
  } catch (e) {
    assert(e.code === "INVALID_JSON", "code INVALID_JSON, got " + e.code);
    assert(!p._hasCache("items"), "must not cache after invalid JSON");
  }
  const callsAfterFail = networkCalls;
  const second = await p.list("items", { page: 1, pageSize: 8 });
  assert(networkCalls === callsAfterFail + 1, "retry must hit network, calls=" + networkCalls);
  assert(second.rows.length === 1, "recovery rows");
  assert(second.total === 1, "recovery total");
  return { case: "HTTP 200 invalid JSON", failedResponseCached: false, networkCalls, recovered: true };
}

async function caseBadShape() {
  networkCalls = 0;
  scripted = [
    { status: 200, body: '{"items":[]}' },
    { status: 200, body: "[]" },
    { status: 200, body: "[]" }
  ];
  const p = new Provider("");
  try {
    await p.list("items", { page: 1, pageSize: 8 });
    throw new Error("expected shape error");
  } catch (e) {
    assert(e.code === "INVALID_COLLECTION", "INVALID_COLLECTION got " + e.code);
    assert(!p._hasCache("items"), "no cache on bad shape");
  }
  const r = await p.list("items", { page: 1, pageSize: 8 });
  assert(Array.isArray(r.rows) && r.rows.length === 0, "empty ok");
  assert(p._hasCache("items"), "real empty array may be cached");
  const calls = networkCalls;
  await p.list("items", { page: 1, pageSize: 8 });
  assert(networkCalls === calls, "cached empty does not refetch");
  await p.list("items", { page: 1, pageSize: 8, forceRefresh: true });
  assert(networkCalls === calls + 1, "forceRefresh refetches");
  return { case: "HTTP 200 bad shape + empty + forceRefresh", ok: true, networkCalls };
}

async function caseHttp500() {
  networkCalls = 0;
  scripted = [
    { status: 500, body: '{"message":"boom","code":"ERR"}' },
    { status: 200, body: '[{"id":2,"sku":"B","name":"Bolt","unitPriceCents":50}]' }
  ];
  const p = new Provider("");
  try {
    await p.list("items", { page: 1, pageSize: 8 });
    throw new Error("expected 500");
  } catch (e) {
    assert(e.status === 500, "status 500");
    assert(!p._hasCache("items"), "500 not cached");
  }
  const r = await p.list("items", { page: 1, pageSize: 8 });
  assert(r.rows.length === 1 && String(r.rows[0].sku) === "B", "recover after 500");
  return { case: "HTTP 500 then recover", ok: true, networkCalls };
}

async function caseStaleSearchOrder() {
  /* Simulate two overlapping list calls: first slow invalid, second valid — provider itself
     is sequential per call; race is handled in selectors. Here verify forceRefresh + no poison. */
  networkCalls = 0;
  scripted = [
    { status: 200, body: '[{"id":1,"sku":"OLD","name":"Old","unitPriceCents":1}]' },
    { status: 200, body: '[{"id":9,"sku":"NEW","name":"New","unitPriceCents":9}]' }
  ];
  const p = new Provider("");
  const a = await p.list("items", { q: "old", page: 1, pageSize: 8 });
  assert(a.rows[0].sku === "OLD", "first");
  const b = await p.list("items", { q: "new", page: 1, pageSize: 8, forceRefresh: true });
  assert(b.rows[0].sku === "NEW", "force refresh gets new server data");
  assert(networkCalls === 2, "two network calls");
  return { case: "search forceRefresh ordering", ok: true, networkCalls };
}

const out = [];
out.push(await caseInvalidJson());
out.push(await caseBadShape());
out.push(await caseHttp500());
out.push(await caseStaleSearchOrder());
console.log(JSON.stringify(out, null, 2));
console.log("ALL_PROVIDER_CACHE_CASES_PASS");
