# 许可隔离区（quarantine）

下列内容在权属/来源核实前，**不得**视为已按根 LICENSE 无条件可发布。  
本目录只放说明，不移动大文件（避免破坏构建）；标记为流程隔离。

| ID | 路径/主题 | 原因 | 建议动作 |
|----|-----------|------|----------|
| Q-1 | `src/test/java/io/jnative/http2/Hpack031.java` | Huffman 码表可能来自 RFC 或其它实现；来源未在文件头证明 | 作者书面确认独立实现或补上游许可 |
| Q-2 | `packages/openssl-tls` 元数据 `license = "Apache-2.0"` | 易与 OpenSSL **库本体**许可混淆 | 澄清包许可证仅覆盖 JNative 封装元数据 |
| Q-3 | `packages/sqlite-native` | 未见随树分发的 sqlite 源；运行时 lib 来源不明 | 写明依赖系统/外部 sqlite 及许可 |
| Q-4 | `examples/https/certs/server.key`（若误入库） | 私钥 | 见敏感信息报告 |
| Q-5 | Android `gradlew` / wrapper | 第三方 Apache-2.0（声明齐全） | 可发布，但必须保留头并列入 notices（非阻断，列此防漏） |
| Q-6 | `tools/vscode-jnative/.vscode-test/**` | 完整 VS Code 测试宿主，不应公开进源码包 | 确保 ignore / 不打包 |

隔离不等于删除原作者声明；核实前禁止重新授权或删除第三方头。
