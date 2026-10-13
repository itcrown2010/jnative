// Author: 张晓达
/**
 * Form controller that never rebuilds inputs on parent re-render.
 * Respects IME composition; illegal dates are not coerced.
 */
import { feedChars, parseDueInput, errMessage, formatDue } from './taskApi.js';

function formatDueYmd(ymd) {
  const n = ymd | 0;
  if (!n) return '';
  try {
    return formatDue(n);
  } catch (_) {
    const s = String(n);
    if (s.length === 8) return s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6, 8);
    return '';
  }
}

export function attachTaskForm(root, { api, mode, taskId, onSubmitOk, onValidated, onCancel }) {
  const titleEl = root.querySelector('[data-field="title"]');
  const notesEl = root.querySelector('[data-field="notes"]');
  const statusEl = root.querySelector('[data-field="status"]');
  const dueEl = root.querySelector('[data-field="due"]');
  const form = root.querySelector('form');
  const errEl = root.querySelector('[data-form-error]');
  const fieldErr = {
    title: root.querySelector('[data-error="title"]'),
    notes: root.querySelector('[data-error="notes"]'),
    status: root.querySelector('[data-error="status"]'),
    due: root.querySelector('[data-error="due"]'),
  };
  const submitBtn = form.querySelector('[type="submit"]');
  let composing = false;
  let submitting = false;
  let baseline = snapshot();

  function snapshot() {
    return JSON.stringify({
      title: titleEl.value,
      notes: notesEl.value,
      status: statusEl.value,
      due: dueEl.value,
    });
  }

  function setFieldError(name, msg) {
    const el = fieldErr[name];
    if (!el) return;
    if (!msg) { el.hidden = true; el.textContent = ''; return; }
    el.hidden = false;
    el.textContent = msg;
  }

  function setFormError(msg) {
    if (!errEl) return;
    if (!msg) { errEl.hidden = true; errEl.textContent = ''; return; }
    errEl.hidden = false;
    errEl.textContent = msg;
  }

  function clearErrors() {
    setFormError('');
    Object.keys(fieldErr).forEach((k) => setFieldError(k, ''));
  }

  function isDirty() {
    return snapshot() !== baseline;
  }

  function markClean() {
    baseline = snapshot();
  }

  function loadFromTask(id) {
    if (!(api.loadFormFromId(id) | 0)) {
      setFormError(errMessage(api.getLastError() | 0));
      return false;
    }
    /* Values are filled by caller via read APIs — loadForm only seeds WASM draft. */
    return true;
  }

  function fillDom(values) {
    titleEl.value = values.title || '';
    notesEl.value = values.notes || '';
    statusEl.value = String(values.status != null ? values.status : 0);
    dueEl.value = values.due || '';
    markClean();
    clearErrors();
  }

  function pushDraftToCore() {
    api.clearFormDraft();
    if (!feedChars(api.appendTitleChar, titleEl.value.trim())) {
      setFieldError('title', errMessage(api.getLastError() | 0));
      return false;
    }
    if (!feedChars(api.appendNoteChar, notesEl.value)) {
      setFieldError('notes', errMessage(api.getLastError() | 0));
      return false;
    }
    const st = Number(statusEl.value);
    if (!(api.setFormStatus(st) | 0)) {
      setFieldError('status', errMessage(api.getLastError() | 0));
      return false;
    }
    const due = parseDueInput(dueEl.value);
    if (!due.ok) {
      setFieldError('due', due.error);
      return false;
    }
    if (!(api.setFormDue(due.ymd) | 0)) {
      setFieldError('due', errMessage(api.getLastError() | 0));
      return false;
    }
    return true;
  }

  function validateLocal() {
    clearErrors();
    if (composing) {
      setFormError('请先完成输入法组合');
      return false;
    }
    if (!titleEl.value.trim()) {
      setFieldError('title', '标题不能为空');
      return false;
    }
    const due = parseDueInput(dueEl.value);
    if (!due.ok) {
      setFieldError('due', due.error);
      return false;
    }
    if (Number(statusEl.value) === 1 && !notesEl.value.trim()) {
      setFieldError('notes', '进行中的任务需要填写备注');
      setFormError('跨字段校验未通过');
      return false;
    }
    return true;
  }

  async function submit(ev) {
    if (ev) ev.preventDefault();
    if (submitting) return;
    if (!validateLocal()) return;
    if (!pushDraftToCore()) return;
    if (!(api.validateForm() | 0)) {
      const code = api.getLastError() | 0;
      if (code === 7) setFieldError('notes', errMessage(code));
      else if (code === 6) setFieldError('due', errMessage(code));
      else if (code === 2) setFieldError('title', errMessage(code));
      else setFormError(errMessage(code));
      return;
    }
    submitting = true;
    submitBtn.disabled = true;
    submitBtn.dataset.state = 'submitting';
    try {
      const due = parseDueInput(dueEl.value);
      const payload = {
        title: titleEl.value.trim(),
        notes: notesEl.value,
        status: Number(statusEl.value) | 0,
        dueYmd: due.ymd | 0,
      };
      if (onValidated) {
        const r = await onValidated(payload, { mode, taskId });
        if (!r || r.ok === false) {
          // Keep inputs; map server field errors when present
          if (r && r.fields && typeof r.fields === 'object') {
            Object.keys(r.fields).forEach((k) => {
              const key = k === 'dueYmd' ? 'due' : k;
              setFieldError(key, r.fields[k]);
            });
          }
          setFormError((r && r.error) || '保存失败（输入已保留）');
          return;
        }
        // Prefer server-authoritative task when provided
        if (r.task) {
          fillDom({
            title: r.task.title || payload.title,
            notes: r.task.notes || '',
            status: r.task.status != null ? r.task.status : payload.status,
            due: r.task.dueYmd ? formatDueYmd(r.task.dueYmd) : dueEl.value,
          });
        }
        markClean();
        submitBtn.dataset.state = 'success';
        if (onSubmitOk) onSubmitOk(r.id != null ? r.id : taskId, r);
        return;
      }
      let id = taskId;
      if (mode === 'new') {
        id = api.addFromForm() | 0;
        if (id < 0) {
          setFormError(errMessage(api.getLastError() | 0));
          return;
        }
      } else {
        if (!(api.updateFromFormById(taskId) | 0)) {
          setFormError(errMessage(api.getLastError() | 0));
          return;
        }
      }
      markClean();
      submitBtn.dataset.state = 'success';
      if (onSubmitOk) onSubmitOk(id);
    } finally {
      submitting = false;
      submitBtn.disabled = false;
      if (submitBtn.dataset.state === 'submitting') delete submitBtn.dataset.state;
    }
  }

  const onCompStart = () => { composing = true; };
  const onCompEnd = () => { composing = false; };
  titleEl.addEventListener('compositionstart', onCompStart);
  titleEl.addEventListener('compositionend', onCompEnd);
  notesEl.addEventListener('compositionstart', onCompStart);
  notesEl.addEventListener('compositionend', onCompEnd);
  form.addEventListener('submit', submit);
  const onKey = (e) => {
    if (e.key === 'Escape' && onCancel) onCancel();
  };
  root.addEventListener('keydown', onKey);

  return {
    fillDom,
    loadFromTask,
    isDirty,
    markClean,
    clearErrors,
    applyFieldErrors(fields) {
      if (!fields || typeof fields !== 'object') return;
      Object.keys(fields).forEach((k) => {
        const key = k === 'dueYmd' ? 'due' : k;
        setFieldError(key, fields[k]);
      });
    },
    focusTitle() { titleEl.focus(); },
    dispose() {
      titleEl.removeEventListener('compositionstart', onCompStart);
      titleEl.removeEventListener('compositionend', onCompEnd);
      notesEl.removeEventListener('compositionstart', onCompStart);
      notesEl.removeEventListener('compositionend', onCompEnd);
      form.removeEventListener('submit', submit);
      root.removeEventListener('keydown', onKey);
    },
  };
}
