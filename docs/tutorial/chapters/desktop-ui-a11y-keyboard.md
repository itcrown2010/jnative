# 桌面 UI：键盘、无障碍与显示缩放

作者：张晓达

## 1. 焦点与 Tab 顺序

```java
nameTf.setTabIndex(0);
ArrayList<Component> order = new ArrayList<Component>();
order.add(nameTf);
order.add(priceTf);
order.add(saveBtn);
FocusTraversal.apply(window, order); // 串联 Cocoa nextKeyView + 聚焦首项
```

- 禁用 / 隐藏 / 已销毁控件：`FocusManager.requestFocus` 直接忽略。
- 表单提交失败：`FocusManager.requestFocus(invalidField)`（见商品编辑器 `focusFirstInvalid`）。

## 2. 默认按钮与取消

```java
window.setDefaultButton(saveBtn);  // Return → Cocoa defaultButtonCell
window.setCancelButton(cancelBtn);  // Escape → cancelBtn.fireClick()
```

文本框内编辑快捷键仍走原生 NSTextField；`ShortcutManager` 不拦截 C/V/X/Z/A。

## 3. 对话框焦点恢复

`Dialogs.info/confirm/error/askSave` 在展示前 `rememberFocus`，结束后 `restoreFocusAfterDialog`。

## 4. 无障碍

```java
saveBtn.setAccessibleName("保存");
saveBtn.setAccessibleDescription("将商品写入磁盘");
field.setAccessibleName("单价");
Bindings.associateErrorLabel(field, errorLabel); // VoiceOver title UI element
```

图标工具栏按钮：`addPushToolButton` 用 tip 作为 accessible name。

**优先原生语义**（按钮标题、复选框标题、系统菜单）。自绘 `TrendChart` 提供只读名称/说明。

### VoiceOver 手工核对（本机）

| 控件 | 期望 |
|------|------|
| 文本框 + 旁路错误标签 | 读出字段名；错误标签可作 title UI |
| 勾选「启用销售」 | 角色复选框 + 标题 |
| 工具栏符号按钮 | tip / accessible name，不只靠图标 |
| 趋势图 | 「趋势图」只读说明 |

未完成：完整 `accessibilityRole` 自定义枚举、表格单元格逐格 AX、Windows MSAA。

## 5. 外观与缩放

```java
Theme.setAppearanceMode(Theme.MODE_SYSTEM); // 或 LIGHT / DARK
int motion = Theme.prefersReducedMotion();     // 1/0/-1
int contrast = Theme.prefersIncreasedContrast();
int scale = Dpi.scalePercentForId(window.nativeId); // 100 或 200…
```

布局使用**逻辑点**；Retina 由 AppKit 光栅化。`TrendChart` 使用 `controlBackgroundColor` / `systemBlueColor` 等语义色，随浅色/深色变化。不要再对视图坐标乘一次 scale。

增强对比度 / 减少动态效果：查询系统偏好；图表不依赖动画即可读。

## 6. 可重复 UI 测试

```bash
# 绑定 + headless UiTest（按 nativeId，非屏幕坐标）
./build/product-editor/ProductEditor --self-test
# 期望: PE_UI_INTEGRATION_OK / PE_SELF_TEST_OK

# 一键冒烟（构建三示例 + AM verify + 短时 .app 启动）
scripts/ui/run-desktop-a11y-smoke.sh
```

`UiTest.focus` / `keyPress` / `toggle` / `click` / `closeWindow` 驱动集成；测试数据写在 `build/product-editor/self-test-product.json`，不碰用户正式库。
