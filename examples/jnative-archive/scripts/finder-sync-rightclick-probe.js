#!/usr/bin/env osascript -l JavaScript
/* Author: 张晓达 — real Finder right-click probe for JNative Finder Sync menu */
ObjC.import('Cocoa');

function run(argv) {
  const testDir = argv[0] || ($.getenv('HOME') + '/JNativeFinderMenuTest');
  const shot = argv[1] || ($.getenv('HOME') + '/jnative/examples/jnative-archive/build/finder-sync-verify/shots/01-context-jnative.png');
  const dump = argv[2] || shot.replace(/\.png$/, '.txt');

  const finder = Application('Finder');
  const se = Application('System Events');
  finder.includeStandardAdditions = true;
  se.includeStandardAdditions = true;

  finder.activate();
  const folder = Path(testDir);
  finder.open(folder);
  delay(0.6);

  // Select a.txt
  try {
    const win = finder.FinderWindows[0];
    const items = win.target.items.whose({ name: 'a.txt' });
    if (items.length > 0) {
      finder.select(items[0]);
    }
  } catch (e) {}
  delay(0.4);

  const proc = se.processes['Finder'];
  proc.frontmost = true;

  // Find AX element for a.txt
  let x = 480, y = 320;
  try {
    const win = proc.windows[0];
    const all = win.entireContents();
    for (let i = 0; i < all.length; i++) {
      const el = all[i];
      let name = '';
      try { name = String(el.name()); } catch (e) {}
      let val = '';
      try { val = String(el.value()); } catch (e) {}
      const hit = (name.indexOf('a.txt') >= 0) || (val.indexOf('a.txt') >= 0);
      if (!hit) continue;
      try {
        const p = el.position();
        const s = el.size();
        x = p[0] + Math.max(8, s[0] / 2);
        y = p[1] + Math.max(8, s[1] / 2);
        break;
      } catch (e) {}
    }
  } catch (e) {}

  // Right-click
  const pt = $.CGPointMake(x, y);
  const down = $.CGEventCreateMouseEvent(null, $.kCGEventRightMouseDown, pt, $.kCGMouseButtonRight);
  const up = $.CGEventCreateMouseEvent(null, $.kCGEventRightMouseUp, pt, $.kCGMouseButtonRight);
  $.CGEventPost($.kCGHIDEventTap, down);
  delay(0.05);
  $.CGEventPost($.kCGHIDEventTap, up);
  delay(0.85);

  let report = 'click=' + x + ',' + y + '\n';
  let foundJNative = false;
  let subTitles = [];

  try {
    const menus = proc.menus();
    report += 'menus=' + menus.length + '\n';
    for (let mi = 0; mi < menus.length; mi++) {
      const m = menus[mi];
      let title = '';
      try { title = String(m.title()); } catch (e) {}
      report += 'MENU ' + (mi + 1) + ' title=' + title + '\n';
      let items = [];
      try { items = m.menuItems(); } catch (e) { continue; }
      for (let ii = 0; ii < items.length; ii++) {
        const item = items[ii];
        let nm = '';
        try { nm = String(item.name()); } catch (e) { nm = '(unnamed)'; }
        report += '  [' + (ii + 1) + '] ' + nm + '\n';
        if (nm === 'JNative' || nm.indexOf('JNative') >= 0) {
          foundJNative = true;
          try {
            // Show submenu by AX
            item.actions['AXShowMenu'].perform();
            delay(0.35);
          } catch (e) {}
          try {
            const sub = item.menus[0].menuItems();
            for (let si = 0; si < sub.length; si++) {
              let sn = '';
              try { sn = String(sub[si].name()); } catch (e) { sn = '?'; }
              subTitles.push(sn);
              report += '      SUB: ' + sn + '\n';
            }
          } catch (e) {
            report += '      SUB_ERR: ' + e + '\n';
          }
        }
      }
    }
  } catch (e) {
    report += 'MENU_ERR: ' + e + '\n';
  }

  // Screenshot + dump via /bin/sh (ObjC system() not available in JXA)
  const app = Application.currentApplication();
  app.includeStandardAdditions = true;
  app.doShellScript('screencapture -x ' + quoted(shot));
  app.doShellScript('printf %s ' + quoted(report) + ' > ' + quoted(dump));

  return JSON.stringify({
    foundJNative: foundJNative,
    subTitles: subTitles,
    click: [x, y],
    reportPath: dump,
    shotPath: shot
  });
}

function quoted(s) {
  return "'" + String(s).replace(/'/g, "'\\''") + "'";
}
