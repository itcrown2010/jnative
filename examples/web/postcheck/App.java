public class App {
    public static void main(String[] args) {
        HttpServer server = HttpServer.listen("127.0.0.1", 18083);
        HttpConnection conn = server.accept();
        HttpServerRequest req = conn.readRequest();
        String cl = req.headers().getOrEmpty("Content-Length");
        System.out.println("cl=[" + cl + "]");
        System.out.println("count=" + req.headers().count);
        int i = 0;
        while (i < req.headers().count) {
            System.out.println("h=" + req.headers().names[i] + ":" + req.headers().values[i]);
            i = i + 1;
        }
        byte[] body = req.bodyBytes();
        int n = 0;
        if (body != null) {
            n = body.length;
        }
        System.out.println("n=" + n);
        conn.send(HttpResponse.textStatus(200, "n=" + n));
        conn.close();
        server.close();
    }
}
