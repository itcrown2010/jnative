#!/usr/bin/env node
/**
 * Real stdio LSP smoke against java -jar target/jnative.jar lsp.
 * Verifies initialize capabilities, completion, signatureHelp, diagnostics.
 */
import { spawn } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(__dirname, '../../..');
const jar = path.join(repo, 'target/jnative.jar');
const outDir = path.join(__dirname, '../verify-artifacts');
fs.mkdirSync(outDir, { recursive: true });

if (!fs.existsSync(jar)) {
  console.error('missing', jar);
  process.exit(2);
}

const results = [];
function pass(name, detail) {
  results.push({ name, ok: true, detail });
  console.log('PASS', name, detail || '');
}
function fail(name, detail) {
  results.push({ name, ok: false, detail });
  console.error('FAIL', name, detail || '');
}

function encode(msg) {
  const body = JSON.stringify(msg);
  return `Content-Length: ${Buffer.byteLength(body, 'utf8')}\r\n\r\n${body}`;
}

class LspClient {
  constructor(proc) {
    this.proc = proc;
    this.buf = Buffer.alloc(0);
    this.nextId = 1;
    this.pending = new Map();
    this.notes = [];
    proc.stdout.on('data', (d) => this.onData(d));
    proc.stderr.on('data', (d) => {
      const s = d.toString('utf8');
      process.stderr.write(s);
      fs.appendFileSync(path.join(outDir, 'lsp-stderr.log'), s);
    });
  }

  onData(chunk) {
    this.buf = Buffer.concat([this.buf, chunk]);
    while (true) {
      const headerEnd = this.buf.indexOf('\r\n\r\n');
      if (headerEnd < 0) return;
      const header = this.buf.slice(0, headerEnd).toString('utf8');
      const m = /Content-Length:\s*(\d+)/i.exec(header);
      if (!m) {
        this.buf = this.buf.slice(headerEnd + 4);
        continue;
      }
      const len = parseInt(m[1], 10);
      const start = headerEnd + 4;
      if (this.buf.length < start + len) return;
      const body = this.buf.slice(start, start + len).toString('utf8');
      this.buf = this.buf.slice(start + len);
      let msg;
      try {
        msg = JSON.parse(body);
      } catch {
        continue;
      }
      if (msg.id != null && (msg.result !== undefined || msg.error)) {
        const p = this.pending.get(msg.id);
        if (p) {
          this.pending.delete(msg.id);
          if (msg.error) p.reject(msg.error);
          else p.resolve(msg.result);
        }
      } else if (msg.method) {
        this.notes.push(msg);
      }
    }
  }

  request(method, params) {
    const id = this.nextId++;
    const msg = { jsonrpc: '2.0', id, method, params };
    this.proc.stdin.write(encode(msg));
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          reject(new Error('timeout ' + method));
        }
      }, 15000);
    });
  }

  notify(method, params) {
    this.proc.stdin.write(encode({ jsonrpc: '2.0', method, params }));
  }

  async waitNote(method, pred, ms = 8000) {
    const t0 = Date.now();
    while (Date.now() - t0 < ms) {
      const hit = this.notes.find((n) => n.method === method && (!pred || pred(n)));
      if (hit) return hit;
      await new Promise((r) => setTimeout(r, 50));
    }
    return null;
  }
}

function lineCol(text, needle, end = true) {
  const idx = end ? text.indexOf(needle) + needle.length : text.indexOf(needle);
  let line = 0;
  let col = 0;
  for (let i = 0; i < idx; i++) {
    if (text[i] === '\n') {
      line++;
      col = 0;
    } else col++;
  }
  return { line, character: col };
}

async function main() {
  const proc = spawn('java', ['-jar', jar, 'lsp'], {
    cwd: repo,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
  const c = new LspClient(proc);

  const init = await c.request('initialize', {
    processId: process.pid,
    rootUri: 'file://' + repo,
    capabilities: {
      textDocument: {
        completion: { completionItem: { snippetSupport: true } },
        signatureHelp: {},
        publishDiagnostics: {},
      },
    },
  });
  const caps = init?.capabilities || {};
  if (caps.completionProvider?.triggerCharacters?.includes('.')) {
    pass('initialize.completionProvider', JSON.stringify(caps.completionProvider));
  } else {
    fail('initialize.completionProvider', JSON.stringify(caps));
  }
  if (caps.signatureHelpProvider?.triggerCharacters?.includes('(')) {
    pass('initialize.signatureHelpProvider', JSON.stringify(caps.signatureHelpProvider));
  } else {
    fail('initialize.signatureHelpProvider', JSON.stringify(caps));
  }
  for (const cap of [
    'definitionProvider',
    'referencesProvider',
    'hoverProvider',
    'documentSymbolProvider',
    'workspaceSymbolProvider',
    'documentFormattingProvider',
  ]) {
    if (caps[cap]) pass('initialize.' + cap, String(caps[cap]));
    else fail('initialize.' + cap, JSON.stringify(caps));
  }
  if (caps.codeActionProvider) pass('initialize.codeActionProvider', JSON.stringify(caps.codeActionProvider));
  else fail('initialize.codeActionProvider', 'missing');
  if (caps.renameProvider) pass('initialize.renameProvider', JSON.stringify(caps.renameProvider));
  else fail('initialize.renameProvider', 'missing');
  c.notify('initialized', {});

  const uri = 'file://' + path.join(repo, 'tools/vscode-jnative/verify-artifacts/Smoke.jn');
  const localSrc = `public class Main {
    public static void main(String[] args) {
        int answer = 42;
        an
    }
}
`;
  c.notify('textDocument/didOpen', {
    textDocument: { uri, languageId: 'jnative', version: 1, text: localSrc },
  });
  await new Promise((r) => setTimeout(r, 400));

  // Must not match the "an" inside "answer" — anchor on the incomplete token line.
  const posAn = (() => {
    const marker = '        an';
    const idx = localSrc.lastIndexOf(marker) + marker.length;
    let line = 0;
    let col = 0;
    for (let i = 0; i < idx; i++) {
      if (localSrc[i] === '\n') {
        line++;
        col = 0;
      } else col++;
    }
    return { line, character: col };
  })();
  const items = await c.request('textDocument/completion', {
    textDocument: { uri },
    position: posAn,
  });
  const arr = Array.isArray(items) ? items : items?.items || [];
  const labels = arr.map((i) => i.label);
  fs.writeFileSync(path.join(outDir, 'completion-locals.json'), JSON.stringify(arr, null, 2));
  if (labels.includes('answer')) pass('completion.local', labels.slice(0, 12).join(','));
  else fail('completion.local', 'got ' + labels.slice(0, 20).join(','));

  const memberSrc = `class Box {
    int value;
    void bump() {}
}
public class Main {
    public static void main(String[] args) {
        Box b = new Box();
        b.
    }
}
`;
  c.notify('textDocument/didChange', {
    textDocument: { uri, version: 2 },
    contentChanges: [{ text: memberSrc }],
  });
  await new Promise((r) => setTimeout(r, 400));
  const posDot = lineCol(memberSrc, 'b.');
  const mem = await c.request('textDocument/completion', {
    textDocument: { uri },
    position: posDot,
  });
  const memArr = Array.isArray(mem) ? mem : mem?.items || [];
  const memLabels = memArr.map((i) => i.label);
  fs.writeFileSync(path.join(outDir, 'completion-members.json'), JSON.stringify(memArr, null, 2));
  if (memLabels.includes('value') && memLabels.includes('bump')) {
    pass('completion.members', memLabels.join(','));
  } else fail('completion.members', memLabels.join(','));

  const sigSrc = `public class Main {
    static int add(int a, int b) { return a + b; }
    public static void main(String[] args) {
        add(1,
    }
}
`;
  c.notify('textDocument/didChange', {
    textDocument: { uri, version: 3 },
    contentChanges: [{ text: sigSrc }],
  });
  await new Promise((r) => setTimeout(r, 400));
  const posSig = lineCol(sigSrc, 'add(1,');
  const sig = await c.request('textDocument/signatureHelp', {
    textDocument: { uri },
    position: posSig,
  });
  fs.writeFileSync(path.join(outDir, 'signature-help.json'), JSON.stringify(sig, null, 2));
  if (sig && sig.signatures?.length && sig.activeParameter === 1) {
    pass('signatureHelp', sig.signatures[0].label + ' active=' + sig.activeParameter);
  } else fail('signatureHelp', JSON.stringify(sig));

  const errSrc = `public class Main {
    public static void main(String[] args) {
        int x = "boom";
    }
}
`;
  c.notify('textDocument/didChange', {
    textDocument: { uri, version: 4 },
    contentChanges: [{ text: errSrc }],
  });
  const diagNote = await c.waitNote(
    'textDocument/publishDiagnostics',
    (n) =>
      n.params?.uri === uri &&
      (n.params.diagnostics || []).some((d) => /incompatible|cannot be converted|type/i.test(d.message || '')),
    10000
  );
  fs.writeFileSync(
    path.join(outDir, 'publish-diagnostics.json'),
    JSON.stringify(diagNote?.params || null, null, 2)
  );
  const typeDiag = (diagNote?.params?.diagnostics || []).find((d) =>
    /incompatible|cannot be converted|type/i.test(d.message || '')
  );
  if (typeDiag) {
    pass(
      'publishDiagnostics',
      `${typeDiag.range?.start?.line}:${typeDiag.range?.start?.character} ${typeDiag.message}`
    );
  } else fail('publishDiagnostics', JSON.stringify(diagNote?.params?.diagnostics || null));

  // Navigation / hover / outline on unsaved buffer
  const navSrc = `public class Main {
    static int add(int a, int b) { return a + b; }
    public static void main(String[] args) {
        int answer = 42;
        int x = answer;
        int y = add(1, 2);
    }
}
`;
  c.notify('textDocument/didChange', {
    textDocument: { uri, version: 5 },
    contentChanges: [{ text: navSrc }],
  });
  await new Promise((r) => setTimeout(r, 500));
  // Position on use of answer
  {
    const idx = navSrc.indexOf('= answer') + '= '.length;
    let line = 0;
    let col = 0;
    for (let i = 0; i < idx; i++) {
      if (navSrc[i] === '\n') {
        line++;
        col = 0;
      } else col++;
    }
    const def = await c.request('textDocument/definition', {
      textDocument: { uri },
      position: { line, character: col + 2 },
    });
    fs.writeFileSync(path.join(outDir, 'definition.json'), JSON.stringify(def, null, 2));
    const arr = Array.isArray(def) ? def : def ? [def] : [];
    if (arr.length && arr[0].uri) pass('definition.local', JSON.stringify(arr[0].range));
    else fail('definition.local', JSON.stringify(def));

    const refs = await c.request('textDocument/references', {
      textDocument: { uri },
      position: { line, character: col + 2 },
      context: { includeDeclaration: true },
    });
    fs.writeFileSync(path.join(outDir, 'references.json'), JSON.stringify(refs, null, 2));
    if (Array.isArray(refs) && refs.length >= 2) pass('references.local', 'count=' + refs.length);
    else fail('references.local', JSON.stringify(refs));

    const hover = await c.request('textDocument/hover', {
      textDocument: { uri },
      position: { line, character: col + 2 },
    });
    fs.writeFileSync(path.join(outDir, 'hover.json'), JSON.stringify(hover, null, 2));
    const hv = hover?.contents?.value || hover?.contents || '';
    if (String(hv).includes('answer') || String(hv).includes('int')) pass('hover.local', String(hv).slice(0, 80));
    else fail('hover.local', JSON.stringify(hover));
  }

  const symbols = await c.request('textDocument/documentSymbol', {
    textDocument: { uri },
  });
  fs.writeFileSync(path.join(outDir, 'document-symbols.json'), JSON.stringify(symbols, null, 2));
  const symNames = (Array.isArray(symbols) ? symbols : []).map((s) => s.name);
  if (symNames.includes('Main')) pass('documentSymbol', symNames.join(','));
  else fail('documentSymbol', JSON.stringify(symbols));

  const wsym = await c.request('workspace/symbol', { query: 'add' });
  fs.writeFileSync(path.join(outDir, 'workspace-symbols.json'), JSON.stringify(wsym, null, 2));
  if (Array.isArray(wsym) && wsym.some((s) => s.name === 'add')) pass('workspaceSymbol', 'add');
  else fail('workspaceSymbol', JSON.stringify(wsym)?.slice(0, 200));

  // Cross-file definition (multi-cu project with package cache)
  const multiMain = path.join(repo, 'examples/multi-cu-resolve/app/src/Main.jn');
  if (fs.existsSync(multiMain)) {
    const multiUri = 'file://' + multiMain;
    const multiText = fs.readFileSync(multiMain, 'utf8');
    c.notify('textDocument/didOpen', {
      textDocument: { uri: multiUri, languageId: 'jnative', version: 1, text: multiText },
    });
    await new Promise((r) => setTimeout(r, 800));
    const uidx = multiText.indexOf('Util.answer');
    let uline = 0;
    let ucol = 0;
    for (let i = 0; i < uidx; i++) {
      if (multiText[i] === '\n') {
        uline++;
        ucol = 0;
      } else ucol++;
    }
    const cdef = await c.request('textDocument/definition', {
      textDocument: { uri: multiUri },
      position: { line: uline, character: ucol + 2 },
    });
    fs.writeFileSync(path.join(outDir, 'definition-crossfile.json'), JSON.stringify(cdef, null, 2));
    const carr = Array.isArray(cdef) ? cdef : cdef ? [cdef] : [];
    if (carr.length && /Util/i.test(carr[0].uri || '')) pass('definition.crossFile', carr[0].uri);
    else fail('definition.crossFile', JSON.stringify(cdef));
  } else {
    fail('definition.crossFile', 'missing multi-cu Main.jn');
  }

  // Rename local + refuse string/comment
  const renSrc = `public class Main {
    public static void main(String[] args) {
        int answer = 42;
        int x = answer;
        String s = "answer";
        // answer comment
    }
}
`;
  c.notify('textDocument/didChange', {
    textDocument: { uri, version: 6 },
    contentChanges: [{ text: renSrc }],
  });
  await new Promise((r) => setTimeout(r, 500));
  {
    const idx = renSrc.indexOf('= answer') + 2;
    let line = 0;
    let col = 0;
    for (let i = 0; i < idx; i++) {
      if (renSrc[i] === '\n') {
        line++;
        col = 0;
      } else col++;
    }
    const prep = await c.request('textDocument/prepareRename', {
      textDocument: { uri },
      position: { line, character: col + 2 },
    });
    fs.writeFileSync(path.join(outDir, 'prepare-rename.json'), JSON.stringify(prep, null, 2));
    if (prep && prep.placeholder === 'answer') pass('prepareRename', JSON.stringify(prep.range));
    else fail('prepareRename', JSON.stringify(prep));

    const ren = await c.request('textDocument/rename', {
      textDocument: { uri },
      position: { line, character: col + 2 },
      newName: 'result',
    });
    fs.writeFileSync(path.join(outDir, 'rename.json'), JSON.stringify(ren, null, 2));
    const changes = ren?.changes || {};
    const edits = Object.values(changes).flat();
    if (edits.length >= 2 && edits.every((e) => e.newText === 'result')) {
      pass('rename.local', 'edits=' + edits.length);
    } else fail('rename.local', JSON.stringify(ren)?.slice(0, 300));
  }

  // Document formatting
  const fmtSrc = 'public class Main{public static void main(String[] args){int x=1+2;System.out.println("a  b");}}\n';
  c.notify('textDocument/didChange', {
    textDocument: { uri, version: 7 },
    contentChanges: [{ text: fmtSrc }],
  });
  await new Promise((r) => setTimeout(r, 400));
  const fmt = await c.request('textDocument/formatting', {
    textDocument: { uri },
    options: { tabSize: 4, insertSpaces: true },
  });
  fs.writeFileSync(path.join(outDir, 'formatting.json'), JSON.stringify(fmt, null, 2));
  if (Array.isArray(fmt) && fmt.length === 1 && fmt[0].newText && fmt[0].newText.includes('"a  b"')) {
    pass('formatting.document', 'len=' + fmt[0].newText.length);
  } else fail('formatting.document', JSON.stringify(fmt)?.slice(0, 200));

  // Build JSON diagnostics path
  const buildDir = path.join(outDir, 'build-smoke');
  fs.mkdirSync(path.join(buildDir, 'src'), { recursive: true });
  fs.writeFileSync(
    path.join(buildDir, 'jnative.project'),
    JSON.stringify(
      {
        name: 'build-smoke',
        source: ['src'],
        main: 'Main',
        output: 'build/build-smoke',
      },
      null,
      2
    )
  );
  fs.writeFileSync(
    path.join(buildDir, 'src/Main.java'),
    'public class Main {\n  public static void main(String[] args) {\n    int x = "bad";\n  }\n}\n'
  );
  const { spawnSync } = await import('child_process');
  const br = spawnSync(
    'java',
    ['-jar', jar, 'build', '--project', buildDir, '--format', 'json'],
    { encoding: 'utf8', cwd: repo, timeout: 120000 }
  );
  fs.writeFileSync(path.join(outDir, 'build-stdout.txt'), br.stdout || '');
  fs.writeFileSync(path.join(outDir, 'build-stderr.txt'), br.stderr || '');
  let report;
  try {
    const lines = (br.stdout || '').trim().split(/\n/);
    const jsonLine = [...lines].reverse().find((l) => l.trim().startsWith('{'));
    report = JSON.parse(jsonLine || '{}');
  } catch (e) {
    report = { parseError: String(e), stdout: br.stdout };
  }
  fs.writeFileSync(path.join(outDir, 'build-report.json'), JSON.stringify(report, null, 2));
  if (report.success === false && Array.isArray(report.diagnostics) && report.diagnostics.length) {
    const d = report.diagnostics.find((x) => x.line > 0);
    if (d) pass('build.jsonDiagnostics', `${d.file}:${d.line}:${d.column} ${d.message}`);
    else fail('build.jsonDiagnostics', JSON.stringify(report.diagnostics));
  } else {
    fail('build.jsonDiagnostics', `code=${br.status} report=${JSON.stringify(report).slice(0, 200)}`);
  }

  c.notify('shutdown', {});
  try {
    await c.request('shutdown', null);
  } catch {
    /* ignore */
  }
  c.notify('exit', {});
  proc.kill();

  const summary = {
    when: new Date().toISOString(),
    jar,
    results,
    passed: results.filter((r) => r.ok).length,
    failed: results.filter((r) => !r.ok).length,
  };
  fs.writeFileSync(path.join(outDir, 'smoke-summary.json'), JSON.stringify(summary, null, 2));

  // HTML evidence page for screenshots
  const html = `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"/><title>JNative LSP 0.3.0 验收证据</title>
<style>
body{font-family:ui-sans-serif,system-ui;margin:24px;background:#0f1419;color:#e7ecf1}
h1{font-size:22px;margin:0 0 8px}
.sub{color:#8b9aab;margin-bottom:24px}
.card{background:#1a2332;border:1px solid #2a3a4f;border-radius:8px;padding:16px;margin:12px 0}
.ok{color:#3dd68c}.bad{color:#f07178}
pre{background:#0b1016;padding:12px;overflow:auto;font-size:12px;border-radius:6px}
.tag{display:inline-block;padding:2px 8px;border-radius:4px;background:#243044;margin-right:8px}
</style></head><body>
<h1>JNative VS Code 插件 0.3.0 — LSP / 构建验收</h1>
<p class="sub">真实 stdio 语言服务 + <code>build --format json</code>（非符号表假数据） · ${summary.when}</p>
<div class="card"><strong>结果</strong>：
  <span class="${summary.failed ? 'bad' : 'ok'}">${summary.passed} passed / ${summary.failed} failed</span>
</div>
${results
  .map(
    (r) => `<div class="card"><span class="tag ${r.ok ? 'ok' : 'bad'}">${r.ok ? 'PASS' : 'FAIL'}</span>
  <strong>${r.name}</strong><pre>${escape(r.detail || '')}</pre></div>`
  )
  .join('')}
<div class="card"><strong>补全（局部变量）</strong><pre>${escape(
    fs.readFileSync(path.join(outDir, 'completion-locals.json'), 'utf8').slice(0, 1200)
  )}</pre></div>
<div class="card"><strong>补全（成员）</strong><pre>${escape(
    fs.readFileSync(path.join(outDir, 'completion-members.json'), 'utf8').slice(0, 1200)
  )}</pre></div>
<div class="card"><strong>参数提示</strong><pre>${escape(
    fs.readFileSync(path.join(outDir, 'signature-help.json'), 'utf8')
  )}</pre></div>
<div class="card"><strong>编辑诊断 publishDiagnostics</strong><pre>${escape(
    fs.readFileSync(path.join(outDir, 'publish-diagnostics.json'), 'utf8').slice(0, 1500)
  )}</pre></div>
<div class="card"><strong>构建 JSON 诊断</strong><pre>${escape(
    fs.readFileSync(path.join(outDir, 'build-report.json'), 'utf8').slice(0, 1500)
  )}</pre></div>
</body></html>`;
  function escape(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
  }
  fs.writeFileSync(path.join(outDir, 'acceptance.html'), html);

  console.log('\nSummary:', summary.passed, 'passed,', summary.failed, 'failed');
  console.log('Artifacts:', outDir);
  process.exit(summary.failed ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
