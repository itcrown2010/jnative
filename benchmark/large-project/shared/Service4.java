@Rpc(path = "/rpc/s4", version = 1)
interface Service4 {
    Type4 get(String id);
    int count();
}
