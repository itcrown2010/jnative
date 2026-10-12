# 发布前最终核对表

全部勾选且无阻断项后，再由用户**另行决定**是否公开。

## 署名与许可

- [x] `USER_CONFIRMATIONS.md` §A：公开姓名已确认（张晓达）
- [x] `LICENSE` 版权行已为确认姓名；**MIT 条款未改**
- [x] `NOTICE`、`COPYRIGHT.md` 与 LICENSE 版权行一致
- [x] 自有源文件头已按 `HEADER_POLICY.md` 添加；`mvn -DskipTests package` 通过
- [x] 未对第三方文件改署为个人版权（含跳过 `Hpack031.java`）
- [x] 未添加与 MIT 冲突的限制条款

## 第三方与发行物

- [ ] `THIRD_PARTY_NOTICES.md` 与 `third_party_licenses/` 已随 SDK/源码包检查
- [ ] VSIX 构建含 languageclient 归属说明
- [ ] 文档已说明：用户程序链接 OpenSSL/zlib 时的归属责任（见生成程序边界文）
- [ ] quarantine 项已关闭或明确「带风险公开」

## 敏感信息

- [ ] 工作区无私钥（`server.key` 等）；有效密钥已轮换
- [ ] 演示口令已占位符化
- [ ] 知悉 Git 历史可能仍含作者邮箱；净化另案审批

## 贡献与品牌

- [ ] `CONTRIBUTING.md` 已定稿（PR ≠ 转让版权）
- [ ] 品牌说明未宣称已注册商标

## 打包抽查

- [ ] 源码树含 `LICENSE` / `NOTICE` / `THIRD_PARTY_NOTICES.md`
- [ ] `jnative.jar` 旁或发行说明含许可指针
- [ ] VSIX / WASM 示例发行说明已抽查

## 签字栏（人工）

- 核对人：__________  日期：__________  
- 用户确认公开：__________  日期：__________
