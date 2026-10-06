@Rpc(path = "/rpc/s3", version = 1)
interface Service3 {
    Type3 get(String id);
    int count();
}
