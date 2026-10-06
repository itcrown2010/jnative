public class HttpDownload {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("usage: jhttpget URL output.file");
            return;
        }
        HttpResponse response = HttpClient.get(args[0]);
        File.writeBytes(args[1], response.bodyBytes());
        System.out.println(response.statusCode());
    }
}
