// Author: 张晓达
/**
 * Layered client diagnostics for Web / WebView.
 * Does not invent .jn line numbers; redacts tokens / long secrets.
 */

let seq = 0;

export function newEventId(prefix) {
  seq += 1;
  return `${prefix || 'e'}-${Date.now().toString(36)}-${seq}`;
}

function redact(value) {
  if (value == null) return '';
  const s = String(value);
  return s
    .replace(/(authorization|token|password|secret|api[_-]?key)\s*[:=]\s*\S+/gi, '$1=<redacted>')
    .replace(/("(?:token|password|secret|authorization)"\s*:\s*")[^"]+"/gi, '$1<redacted>"')
    .slice(0, 800);
}

/**
 * @param {'jnative-compile'|'wasm'|'javascript'|'bridge'|'network'|'container'} layer
 * @param {string} message
 * @param {{ eventId?: string, detail?: unknown }} [extra]
 */
export function diag(layer, message, extra) {
  const eventId = (extra && extra.eventId) || newEventId('ui');
  const detail = extra && extra.detail != null ? redact(typeof extra.detail === 'string' ? extra.detail : JSON.stringify(extra.detail)) : '';
  const line = `[${eventId}] [${layer}] ${redact(message)}${detail ? ' | ' + detail : ''}`;
  if (layer === 'javascript' || layer === 'wasm' || layer === 'bridge' || layer === 'network') {
    console.error(line);
  } else {
    console.log(line);
  }
  try {
    const el = document.getElementById('diag-log');
    if (el) {
      const row = document.createElement('div');
      row.className = `diag diag-${layer}`;
      row.textContent = line;
      el.prepend(row);
      while (el.childElementCount > 40) el.lastChild.remove();
    }
  } catch {
    /* ignore */
  }
  return eventId;
}

export function installGlobalHandlers() {
  window.addEventListener('error', (ev) => {
    diag('javascript', ev.message || 'window.error', {
      detail: { file: ev.filename, line: ev.lineno, col: ev.colno },
    });
  });
  window.addEventListener('unhandledrejection', (ev) => {
    const reason = ev.reason;
    const msg = reason && reason.message ? reason.message : String(reason);
    diag('javascript', 'unhandledrejection: ' + msg);
  });
}
