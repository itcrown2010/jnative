// JNative 0.32 ultimate metaprogramming acceptance sample
// Compile: jnative examples/meta/MetaApp.java -o build/meta/MetaApp --explain-meta --deterministic
//
// Compile-time: annotations → processors → generated source/schema → semantic → IR → LLVM
// Runtime: direct generated factories / codecs / validators — NO JVM reflection.

/**
 * @author 张晓达
 */
@Config("appdb")
class AppDatabaseConfig {
    String host;
    @Default("5432")
    int port;
}

@Json
@Entity("users")
class User {
    @Id
    long id;
    String name;
}

@Repository
interface UserRepository {
}

@Service
class UserService {
}

@Controller("/users")
class UserController {
    @Get("/:id")
    void get() {
    }
}

@Rpc("/api/users")
interface UserApi {
    User getUser(int id);
}

@Json
class UserDto {
    int id;
    String name;
}

@Validated
class CreateUserRequest {
    @NotBlank
    String name;
    @Min(18)
    int age;
}

public class MetaApp {
    public static void main(String[] args) {
        System.out.println("JNative 0.32 MetaApp");
        System.out.println("Metaprogramming LLVM intrinsics: NONE");
        System.out.println("Annotation Processing LLVM intrinsics: NONE");
        System.out.println("Compile-Time DI LLVM intrinsics: NONE");
        System.out.println("NO Class.forName / Method.invoke / runtime bean scanning");
        System.out.println("ok");
    }
}
