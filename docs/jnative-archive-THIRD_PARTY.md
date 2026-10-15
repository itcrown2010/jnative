# JNative Archive — 第三方依赖

作者：张晓达

## minizip-ng

| 项 | 值 |
|----|-----|
| 项目 | [zlib-ng/minizip-ng](https://github.com/zlib-ng/minizip-ng) |
| 版本 | **4.0.8**（`examples/jnative-archive/third_party/VERSION.txt`） |
| 许可 | 见上游 `LICENSE`（zlib 风格） |
| 获取 | `examples/jnative-archive/scripts/fetch-deps.sh` |
| 产物 | 静态库 `libminizip-ng.a` / Windows `.lib` |

### 固定 CMake 选项

- `MZ_WZAES=ON` — WinZip AES
- `MZ_PKCRYPT=OFF` — 禁用传统 ZipCrypto，避免静默降级
- `MZ_BZIP2/LZMA/ZSTD/LIBCOMP/OPENSSL=OFF`（阶段 A；阶段 E 另议 7z）
- `BUILD_SHARED_LIBS=OFF`
- `MZ_FETCH_LIBS=OFF`（macOS/Linux，使用系统 zlib）；Windows 构建可按需 `ON` 拉取缺失依赖

### 平台加密后端（阶段 A/B）

- macOS：Apple CommonCrypto + Security.framework（`mz_crypt_apple.c`）
- Windows：按 minizip-ng 配置（构建日志记录实际后端）
- ZIP 加密格式：**WinZip AES**（默认 AES-256，AE-1/AE-2）；**ZipCrypto 关闭**
- 密码 UTF-8；不写日志、配置、argv 或任务历史
- ZIP 模式不加密中央目录文件名（见阶段 B 报告）

### zlib

系统 zlib（`-lz`）。不捆绑第二份 zlib，除非 Windows 构建无法找到系统库（届时在构建日志中标明来源）。

## 非依赖声明

- **不**将 JNative 编译器宿主 JDK 的 `java.util.zip` 当作运行时实现。
- **不**在最终产品中 `exec` 用户环境的 `zip`/`unzip`/`7z`/`rar`。
- 阶段 E 的 7-Zip 引擎若引入，将单独固定版本与许可证条目，并验证归档+加密完整性后再启用。
