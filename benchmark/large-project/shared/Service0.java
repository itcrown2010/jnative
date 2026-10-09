/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s0", version = 1)
interface Service0 {
    Type0 get(String id);
    int count();
}
