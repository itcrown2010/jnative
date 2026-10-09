import jnative.web.*;
import jnative.json.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        EventLoop loop = new EventLoop();
        WebApp app = new WebApp();
        app.bindEventLoop(loop);
        app.futurePost("/api/register", new Reg(loop));
        System.out.println("dbg listening 18259");
        app.listenWithLoop(18259);
    }
}

class Reg implements AsyncWebHandler {
    EventLoop loop;
    Reg(EventLoop loop) { this.loop = loop; }
    public JnFuture handle(WebContext ctx) {
        JsonObject body = Json.parse(ctx.bodyString()).asObject();
        String password = body.getString("password", "");
        String hash = Password.hashBlocking(password);
        ctx.status(201).jsonString("{\"ok\":true,\"hash\":\"" + hash + "\"}");
        return JnFuture.completed(loop, null);
    }
}
