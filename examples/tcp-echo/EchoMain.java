/**
 * One-shot TCP echo on 127.0.0.1:19090.
 * @author 张晓达
 */
public class EchoMain {
    public static void main(String[] args) {
        TcpServer server = TcpServer.listen("127.0.0.1", 19090);
        System.out.println("listening 19090");
        TcpSocket client = server.accept();
        byte[] data = client.readBytes(1024);
        client.write(data);
        client.close();
        server.close();
        System.out.println("echoed");
    }
}
