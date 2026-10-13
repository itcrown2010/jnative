// Author: 张晓达
/**
 * Lightweight page/controller lifecycle for the JS shell.
 * Not WebUI Html.* (that path remains experimental).
 * Guarantees: mount once per instance id; dispose cancels subs + async.
 */

let seq = 1;

export function createController(name, hooks, stableId) {
  const id = stableId || (name + '#' + (seq++));
  let mounted = false;
  const unsubs = [];
  const aborts = [];
  const children = [];

  const ctl = {
    id,
    name,
    alive: true,
    mount(ctx) {
      if (mounted) return;
      mounted = true;
      ctl.alive = true;
      if (hooks.onMount) hooks.onMount(ctx, ctl);
    },
    update(ctx) {
      if (!mounted || !ctl.alive) return;
      if (hooks.onUpdate) hooks.onUpdate(ctx, ctl);
    },
    dispose() {
      if (!ctl.alive && !mounted) return;
      ctl.alive = false;
      for (let i = children.length - 1; i >= 0; i--) children[i].dispose();
      children.length = 0;
      while (unsubs.length) {
        try { unsubs.pop()(); } catch (_) {}
      }
      while (aborts.length) {
        try { aborts.pop().abort(); } catch (_) {}
      }
      if (hooks.onDispose) {
        try { hooks.onDispose(); } catch (_) {}
      }
      mounted = false;
    },
    track(unsub) {
      if (typeof unsub === 'function') unsubs.push(unsub);
      return unsub;
    },
    listen(target, type, fn, opts) {
      target.addEventListener(type, fn, opts);
      unsubs.push(() => target.removeEventListener(type, fn, opts));
    },
    /** Register AbortController; aborted on dispose. */
    abortable() {
      const c = typeof AbortController !== 'undefined' ? new AbortController() : { abort() {}, signal: undefined };
      aborts.push(c);
      return c;
    },
    child(childCtl) {
      children.push(childCtl);
      return childCtl;
    },
  };
  return ctl;
}

export function createPageHost() {
  let current = null;
  return {
    show(ctl, ctx) {
      if (current && current.id === ctl.id) {
        current.update(ctx);
        return current;
      }
      if (current) current.dispose();
      current = ctl;
      ctl.mount(ctx);
      ctl.update(ctx);
      return ctl;
    },
    dispose() {
      if (current) current.dispose();
      current = null;
    },
    current() {
      return current;
    },
  };
}
