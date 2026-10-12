# 源文件版权 / SPDX 声明策略

## 已确认前提

- 用户有权拥有的 **原创** JNative 代码：版权主体为**个人**（非公司）。
- 根许可证保持 **MIT**（`SPDX-License-Identifier: MIT`）。
- **公开署名**：`张晓达`（见 `USER_CONFIRMATIONS.md` §A，2026-10-12 确认）。

## 自有文件模板

### Java / `.jn` / TypeScript

```java
/*
 * Copyright (c) YYYY 张晓达
 * SPDX-License-Identifier: MIT
 */
```

年份：依据该文件真实首次加入年份（`git log --follow --diff-filter=A --date=format:%Y`），禁止伪造多年跨度。

### C / 头文件 / ObjC

```c
/* Copyright (c) YYYY 张晓达
 * SPDX-License-Identifier: MIT
 */
```

## 禁止

- 覆盖 Gradle Wrapper、npm 厂商文件等第三方原有署名
- 向 `.wasm`、数据库、无注释 JSON、生成索引强行插入
- 添加「禁止商用」等与 MIT 冲突的文字
- 将 quarantine / 不明来源文件标成个人版权（当前：`Hpack031.java`）

## 执行顺序

1. ~~更新 `LICENSE` / `NOTICE` / `COPYRIGHT.md` 版权行~~（已完成）
2. ~~对已核实自有路径加头~~（`src/main|test/java/io/jnative`、`native`、`stdlib`、`tools/vscode-jnative/src`）
3. 构建抽检：`mvn -q -DskipTests package` + 关键示例
4. 更新 `FILE_CHANGE_LIST.md`
