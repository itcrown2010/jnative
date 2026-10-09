import jnative.web.*;

/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        WebApp app = new WebApp();
        app.use(WebMiddleware.logger());
        app.staticFiles("/", "examples/web/staticsite/public");
        app.get("/api/info", new SiteInfoHandler());
        app.listen(18181);
    }
}

class SiteInfoHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.text("JNative static site demo");
    }
}
