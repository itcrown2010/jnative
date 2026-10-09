import jnative.web.*;

/**
 * SSE metrics demo (0.25) — streams connection/memory-ish ticks.
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        EventLoop loop = new EventLoop();
        WebApp app = new WebApp();
        app.bindEventLoop(loop);
        app.use(WebMiddleware.logger());
        app.get("/", new SseIndexHandler());
        app.get("/events", new SseEventsHandler());
        System.out.println("sse demo listening on 18253");
        app.listenWithLoop(18253);
    }
}

class SseIndexHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.html("<!doctype html><html><body><h1>JNative SSE</h1>"
                + "<pre id=out></pre><script>"
                + "const es=new EventSource('/events');"
                + "es.addEventListener('tick',(e)=>{out.textContent+=e.data+'\\n'});"
                + "</script></body></html>");
    }
}

class SseEventsHandler implements WebHandler {
    public void handle(WebContext ctx) {
        SseSupport.applyHeadersOnContext(ctx);
        String body = "";
        int i = 0;
        while (i < 5) {
            SseEvent ev = new SseEvent();
            ev.event = "tick";
            ev.id = String.valueOf(i);
            ev.data = "connections=1\nrequests=" + String.valueOf(i)
                    + "\nmemory=n/a\ngc=n/a";
            body = body + SseStream.encode(ev);
            i = i + 1;
        }
        ctx.status(200);
        ctx.bytes(Bytes.fromString(body));
    }
}
