/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s17", version = 1)
interface Service17 {
    Type17 get(String id);
    int count();
}
