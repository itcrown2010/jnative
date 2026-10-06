@Rpc(path = "/rpc/s9", version = 1)
interface Service9 {
    Type9 get(String id);
    int count();
}
