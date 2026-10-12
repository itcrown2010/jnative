# 拟议元数据补丁（审阅用，默认未自动合并进构建描述）

在用户勾选 `USER_CONFIRMATIONS.md` 中「批准写入 license 元数据」后，可应用如下片段。

## pom.xml（在 `<description>` 后）

```xml
    <url>https://github.com/itcrown2010/jnative</url>
    <licenses>
        <license>
            <name>MIT License</name>
            <url>https://opensource.org/licenses/MIT</url>
            <distribution>repo</distribution>
        </license>
    </licenses>
```

（`url` 以你最终公开地址为准。）

## tools/vscode-jnative/package.json

```json
"license": "MIT",
```

## README 许可小节（建议）

```markdown
## Licensing

本项目源码以 MIT 许可（见 `LICENSE`）。第三方组件见 `THIRD_PARTY_NOTICES.md`。
生成程序的许可边界见 `docs/legal/GENERATED_ARTIFACT_LICENSE_BOUNDARY.md`。
```
