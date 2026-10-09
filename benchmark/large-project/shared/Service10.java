/**
 * @author 张晓达
 */
@Rpc(path = "/rpc/s10", version = 1)
interface Service10 {
    Type10 get(String id);
    int count();
}
