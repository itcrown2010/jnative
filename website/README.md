# JNative 开发者文档站

作者：张晓达

基于 [VitePress](https://vitepress.dev/) 的中文优先文档站。

## 本地预览

```bash
cd website
npm install
npm run dev
```

## 静态构建

```bash
npm run build
# 产物：website/.vitepress/dist
npm run preview
```

**不自动公开部署。** 需要托管时由维护者手动发布 `dist`。

## 内容结构

| 路径 | 内容 |
|------|------|
| `guide/` | 入门 |
| `language/` | 语言指南 |
| `api/` | API 参考（`_generated/` 为提取物） |
| `tutorials/` | 分步教程 |
| `samples/` | 示例说明 |
| `reference/` | 矩阵、版本、调试、阶段 |
| `en/` | 英文结构占位 |

元数据与事实清单：`../docs/developer/`。

## 验证

```bash
../scripts/docs/verify-phase1.sh
```
