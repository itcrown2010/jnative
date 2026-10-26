/**
 * Minimal HTTP/1.1 server (stdlib HttpServer on TCP).
 * @author 张晓达
 */
public class HttpHelloServer {
    public static void main(String[] args) {
        HttpServer server = HttpServer.listen("127.0.0.1", 18081);
        HttpConnection connection = server.accept();
        HttpServerRequest request = connection.readRequest();
        connection.send(HttpResponse.textStatus(200, "Hello JNative"));
        connection.close();
        server.close();
    }
}
