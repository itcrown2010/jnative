@Rpc(path = "/rpc/s13", version = 1)
interface Service13 {
    Type13 get(String id);
    int count();
}
