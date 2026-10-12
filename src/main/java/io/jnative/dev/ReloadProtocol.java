/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.dev;

/** Reload / diagnostics messages pushed to the browser overlay.
 * @author 张晓达
 */
public final class ReloadProtocol {
    public static final String RELOAD_PAGE = "RELOAD_PAGE";
    public static final String RELOAD_CSS = "RELOAD_CSS";
    public static final String BUILD_ERROR = "BUILD_ERROR";
    public static final String BUILD_OK = "BUILD_OK";
    public static final String SERVER_RESTART = "SERVER_RESTART";

    private ReloadProtocol() {
    }

    public static String json(String type, String payload) {
        String p = payload == null ? "" : payload
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
        return "{\"type\":\"" + type + "\",\"payload\":\"" + p + "\"}";
    }

    /** Dev overlay + SSE client injected into HTML pages (not WASM). */
    public static String overlayScript() {
        return """
                <script>
                (function(){
                  if (window.__jnativeDev) return;
                  window.__jnativeDev = true;
                  var overlay = document.createElement('div');
                  overlay.id = '__jnative_overlay';
                  overlay.style.cssText = 'display:none;position:fixed;inset:0;z-index:2147483647;background:rgba(20,20,20,.92);color:#f5f5f5;font:14px/1.5 ui-monospace,Menlo,monospace;padding:24px;overflow:auto;white-space:pre-wrap';
                  document.addEventListener('DOMContentLoaded', function(){ document.body.appendChild(overlay); });
                  if (document.body) document.body.appendChild(overlay);
                  function show(msg){ overlay.style.display='block'; overlay.textContent=msg; }
                  function hide(){ overlay.style.display='none'; overlay.textContent=''; }
                  function connect(){
                    var es = new EventSource('/__jnative/events');
                    es.onmessage = function(ev){
                      try {
                        var m = JSON.parse(ev.data);
                        if (m.type === 'RELOAD_CSS') {
                          var links = document.querySelectorAll('link[rel=stylesheet]');
                          for (var i=0;i<links.length;i++){
                            var href = links[i].getAttribute('href');
                            if (!href) continue;
                            var u = href.split('?')[0] + '?t=' + Date.now();
                            links[i].setAttribute('href', u);
                          }
                          hide();
                        } else if (m.type === 'RELOAD_PAGE' || m.type === 'BUILD_OK') {
                          hide();
                          location.reload();
                        } else if (m.type === 'BUILD_ERROR') {
                          show(m.payload || 'Build failed');
                        }
                      } catch(e){}
                    };
                    es.onerror = function(){ es.close(); setTimeout(connect, 1000); };
                  }
                  connect();
                })();
                </script>
                """;
    }
}
