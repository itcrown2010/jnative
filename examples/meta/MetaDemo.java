// JNative 0.32 — compile-time metaprogramming demo
// jnative examples/meta/MetaDemo.java -o build/meta/MetaDemo --explain-meta

@Json
@Validated
class UserDto {
    int id;
    @NotBlank
    String name;
}

@Config("metadb")
class MetaDatabaseConfig {
    String host;
    @Default("5432")
    int port;
}

@Service
class UserService {
}

public class MetaDemo {
    public static void main(String[] args) {
        System.out.println("Metaprogramming LLVM intrinsics: NONE");
        System.out.println("Annotation Processing LLVM intrinsics: NONE");
        System.out.println("Compile-Time DI LLVM intrinsics: NONE");
        System.out.println("ok");
    }
}
