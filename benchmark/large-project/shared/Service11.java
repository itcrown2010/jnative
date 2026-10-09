/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s11", version = 1)
interface Service11 {
    Type11 get(String id);
    int count();
}
