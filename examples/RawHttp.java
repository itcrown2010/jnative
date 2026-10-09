/**
 * @author 张晓达
 */
public class RawHttp {
    public static void main(String[] args) {
        TcpSocket socket = TcpSocket.connect("example.com", 80);
        socket.writeString(
                "GET / HTTP/1.0\r\n" +
                "Host: example.com\r\n" +
                "\r\n"
        );
        String response = socket.readAllString();
        System.out.println(response);
        socket.close();
    }
}
