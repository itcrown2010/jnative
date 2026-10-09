# 国际化

文案在 `desktop/ActivityMonitor.java` 的 `AmI18n` 中维护（`zh` / `en`）。

- 默认：中文  
- `Env.get("LANG")` 以 `en` 开头时使用英文  

未使用外部 `.properties`；与现有示例一致，避免额外资源管道。
