/**
 * Full-stack users server (native). Pair with client WASM under client/.
 * Shared DTO: ../shared/User.java (same source model).
 */
import jnative.web.*;

public class Server {
    public static void main(String[] args) {
        WebApp app = new WebApp();
        app.get("/api/users", new UsersHandler());
        System.out.println("fullstack users server on 18300");
        System.out.println("Use HTTPS via trusted reverse proxy in production.");
        app.listen(18300);
    }
}

class UsersHandler implements WebHandler {
    public void handle(WebContext ctx) {
        ctx.jsonString("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");
    }
}
