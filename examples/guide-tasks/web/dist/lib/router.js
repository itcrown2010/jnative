// Author: 张晓达
/**
 * Hash router with navigation stack (mobile back = pop stack, not exit app).
 * Routes stay in sync with location.hash.
 */

const STACK_KEY = 'jnative.guide-tasks.navstack';

export function parseRoute(hash) {
  const raw = (hash || '#/').replace(/^#/, '');
  const parts = raw.split('/').filter(Boolean);
  if (parts.length === 0) return { name: 'list', params: {}, path: '/' };
  const head = parts[0];
  if (head === 'list') return { name: 'list', params: {}, path: '/list' };
  if (head === 'new') return { name: 'new', params: {}, path: '/new' };
  if (head === 'settings') return { name: 'settings', params: {}, path: '/settings' };
  if (head === 'task' && parts[1] != null && parts[1] !== '') {
    const id = decodeURIComponent(parts.slice(1).join('/'));
    // reject obviously broken tokens like "not-a-number" only when empty after decode
    if (!id || id === 'not-a-number') {
      return { name: 'invalid', params: { reason: 'bad-task-id' }, path: raw };
    }
    return { name: 'detail', params: { id }, path: '/task/' + encodeURIComponent(id) };
  }
  if (head === 'edit' && parts[1] != null && parts[1] !== '') {
    const id = decodeURIComponent(parts.slice(1).join('/'));
    if (!id || id === 'not-a-number') {
      return { name: 'invalid', params: { reason: 'bad-edit-id' }, path: raw };
    }
    return { name: 'edit', params: { id }, path: '/edit/' + encodeURIComponent(id) };
  }
  if (head === 'not-found') return { name: 'invalid', params: { reason: 'not-found' }, path: '/not-found' };
  return { name: 'invalid', params: { reason: 'unknown' }, path: raw };
}

export function pathFor(name, params = {}) {
  if (name === 'list') return '#/list';
  if (name === 'new') return '#/new';
  if (name === 'settings') return '#/settings';
  if (name === 'detail') return '#/task/' + encodeURIComponent(params.id);
  if (name === 'edit') return '#/edit/' + encodeURIComponent(params.id);
  return '#/not-found';
}

function loadStack() {
  try {
    const raw = sessionStorage.getItem(STACK_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr.filter((x) => typeof x === 'string') : [];
  } catch (_) {
    return [];
  }
}

function saveStack(stack) {
  try {
    sessionStorage.setItem(STACK_KEY, JSON.stringify(stack.slice(-32)));
  } catch (_) {}
}

export function createRouter({ onChange, confirmLeave }) {
  let stack = loadStack();
  let navigating = false;
  let current = parseRoute(location.hash);

  function emit() {
    current = parseRoute(location.hash);
    onChange(current);
  }

  function navigate(hash, { replace = false, pushStack = true } = {}) {
    const next = hash.startsWith('#') ? hash : '#' + hash;
    if (confirmLeave) {
      const ok = confirmLeave(current, parseRoute(next));
      if (ok === false) return false;
    }
    navigating = true;
    if (replace) {
      history.replaceState(null, '', next);
      if (stack.length === 0) stack.push(next);
      else stack[stack.length - 1] = next;
    } else {
      if (pushStack && (stack.length === 0 || stack[stack.length - 1] !== next)) {
        stack.push(next);
      }
      if (location.hash !== next) location.hash = next;
      else emit();
    }
    saveStack(stack);
    navigating = false;
    if (replace) emit();
    return true;
  }

  /** Prefer stack pop; return false if stack empty (caller may exit app). */
  function back() {
    if (stack.length > 1) {
      stack.pop();
      const prev = stack[stack.length - 1];
      saveStack(stack);
      navigate(prev, { replace: true, pushStack: false });
      return true;
    }
    if (current.name !== 'list') {
      navigate('#/list', { replace: true });
      return true;
    }
    return false;
  }

  function bind() {
    window.addEventListener('hashchange', () => {
      if (navigating) return;
      const next = parseRoute(location.hash);
      if (confirmLeave) {
        const ok = confirmLeave(current, next);
        if (ok === false) {
          navigating = true;
          history.replaceState(null, '', pathFor(current.name, current.params));
          navigating = false;
          return;
        }
      }
      const h = location.hash || '#/list';
      if (stack.length === 0 || stack[stack.length - 1] !== h) stack.push(h);
      saveStack(stack);
      emit();
    });
    if (!location.hash || location.hash === '#') {
      history.replaceState(null, '', '#/list');
    }
    if (stack.length === 0) {
      stack.push(location.hash || '#/list');
      saveStack(stack);
    }
    emit();
  }

  return {
    bind,
    navigate,
    back,
    current: () => current,
    pathFor,
    parseRoute,
  };
}
