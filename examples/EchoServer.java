public class EchoServer {
    public static void main(String[] args) {
        TcpServer server = TcpServer.listen("127.0.0.1", 8080);
        while (true) {
            TcpSocket client = server.accept();
            byte[] data = client.readBytes(1024);
            client.write(data);
            client.close();
        }
    }
}
