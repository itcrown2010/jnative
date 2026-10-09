import jnative.web.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        EventLoop loop = new EventLoop();
        WebApp app = new WebApp();
        app.bindEventLoop(loop);
        app.get("/", new IndexHandler());
        app.websocket("/ws", new EchoWsHandler());
        System.out.println("ws-echo listening on 18250");
        app.listenWithLoop(18250);
    }
}

class IndexHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.html("<!doctype html><html><body>"
                + "<h1>JNative WebSocket Echo</h1>"
                + "<script>"
                + "const ws=new WebSocket('ws://'+location.host+'/ws');"
                + "ws.onopen=()=>ws.send('hello');"
                + "ws.onmessage=(e)=>document.body.append(e.data);"
                + "</script></body></html>");
    }
}

class EchoWsHandler implements WebSocketHandler {
    public void onConnect(WebSocketConnection socket) {
        socket.enqueueOutbound(WebSocketFrameCodec.encodeServer(
                WebSocketOpcode.TEXT,
                Bytes.fromString("welcome")));
    }

    public void onMessage(WebSocketConnection socket, WebSocketMessage message) {
        if (message.isText() != 0) {
            socket.enqueueOutbound(WebSocketFrameCodec.encodeServer(
                    WebSocketOpcode.TEXT,
                    Bytes.fromString("Echo: " + message.text)));
        }
    }
}
