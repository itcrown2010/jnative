import jnative.web.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        WebApp app = new WebApp();
        app.get("/", new MiniHandler());
        HttpServer server = HttpServer.listen("127.0.0.1", 18082);
        System.out.println("min listening");
        HttpConnection conn = server.accept();
        System.out.println("accepted");
        HttpServerRequest req = conn.readRequest();
        System.out.println("got request method=" + req.method() + " path=" + req.path());
        try {
            WebContext ctx = app.dispatch(req);
            System.out.println("dispatched status=" + ctx.response.statusCode + " committed=" + ctx.response.committed);
            HttpResponse httpResp = ctx.response.toHttp();
            conn.send(httpResp);
            System.out.println("sent");
        } catch (RuntimeException ex) {
            System.out.println("outer ex");
        }
        conn.close();
        server.close();
    }
}

class MiniHandler implements WebHandler {
    public void handle(WebContext ctx) {
        System.out.println("handler called");
        ctx.text("mini-webapp");
    }
}
