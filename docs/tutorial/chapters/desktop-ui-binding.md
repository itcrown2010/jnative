# 桌面 UI：状态绑定与表单校验

作者：张晓达

目标：用显式、类型安全的绑定把状态接到原生控件上——不反射、不建大型 MVVM。

依赖：`stdlib/ui/BindingLib.java`（随 UI 标准库一并引入）。平台：macOS Cocoa（首期）。

## 1. 可观察状态

```java
ObservableValue<String> name = ObservableValue.ofString("");
ObservableInt enabled = ObservableValue.ofBool(true); // 存 0/1

ObsSubscription sub = name.subscribe((ev) -> {
    // ev.oldValue / ev.newValue
});
name.set("茶");     // 相等则跳过通知
name.setForce("茶"); // 强制通知
sub.cancel();       // 幂等

name.beginUpdate();
name.set("a");
name.set("b");      // 批量内不通知
name.endUpdate();   // 若最终值相对 batchOld 有变，通知一次
```

规则：

| 项 | 行为 |
|----|------|
| 线程 | `set` / `subscribe` / `cancel` / `beginUpdate` / `endUpdate` 必须在 UI 线程 |
| 相等跳过 | `ObservableValue` 对 `String`/`null` 比较；`ObservableInt` 按 int 相等 |
| 通知顺序 | 订阅先后；先提交新值，再在**无内部锁**下对监听器快照回调 |
| 回调异常 | 打印后继续通知其余监听器；可挂 `Application.uncaughtHandler` |
| 嵌套更新 | 通知中再次 `set` 计入 nesting；超过 `MAX_NESTING`（8）抛 `UiException` |

整数请用 `ObservableInt`，避免泛型装箱比较陷阱。

## 2. BindingScope 与释放

```java
BindingScope scope = new BindingScope();
window.onClose(() -> {
    scope.dispose(); // 取消全部绑定与订阅
});
```

- 绑定 API 返回 `BindingHandle`；传入 `scope` 时会登记，随 `dispose` 释放。
- 控件 `disposed` 或 `scope.isDisposed()` 后，回调直接返回，不改原生控件。
- **不能只靠 GC**：原生 peer 与监听器必须显式取消。重新打开窗口时新建 scope，勿复用已 dispose 的实例。

## 3. 控件绑定

```java
Bindings.bindLabel(status, statusText, scope);           // 单向
Bindings.bindTextTwoWay(nameTf, name, scope);            // 双向；setTextIfChanged 保光标
Bindings.bindCheck(enableBox, enabled, scope);           // 布尔 0/1
Bindings.bindComboId(cat, categoryId, categoryIds, scope); // 稳定 id，非行号身份
Bindings.bindEnabled(saveBtn, canSave, scope);
Bindings.bindSegmented(modeSeg, viewMode, scope);
```

双向文本：控件改 → `obs.set`；obs 改 → `setTextIfChanged`（文本相同则不写原生，避免每次按键跳光标）。勾选用可取消的 `subscribeChange`，防止反馈环。`ComboBox<String>` 项文本直接显示；非 String 项须设 `CellRenderer`。

**限制（明确不做）**：无反射扫描；无自动依赖图；数字/日期请用下方字段模型，勿把解析失败静默写成 0。

## 4. 表单校验

```java
StringFieldModel nameField = new StringFieldModel();
nameField.addRule(new RequiredRule("名称必填"));
nameField.addRule(new LengthRule(1, 64, "名称长度 1–64"));
nameField.bind(nameTf, nameErr, scope);

DecimalFieldModel price = new DecimalFieldModel();
price.setRange(Decimal.fromString("0.01"), Decimal.fromString("999999.99"), "单价超范围");
price.addRule(new RequiredRule("单价必填"));
price.bind(priceTf, priceErr, scope); // raw 与 parsed 分离

FormModel form = new FormModel();
form.addString(nameField);
form.addDecimal(price);
form.addCross(() -> { /* 跨字段；返回 null 表示通过 */ return null; });

if (form.validateSubmit() == 0) { /* 提交再验 */ }
form.setServerError("保存失败：…"); // 服务端/持久化错误入口
```

字段状态：`FieldState.UNTOUCHED` / `EDITING` / `INVALID` / `VALID`。输入中用软校验（`revalidate(1)`）；提交用硬校验（`soft=0`）。错误标签贴在字段旁，并用 `Theme.STYLE_ERROR`；完整 VoiceOver 角色映射仍待平台 API。

## 5. 派生与批量

```java
canSave.set(dirty.get() != 0 && nameField.state.get() != FieldState.INVALID ? 1 : 0);
```

自行订阅源状态并写入派生值；或用 `Bindings.bindComputed`（显式 `Runnable`，无隐式追踪）。多项修改用 `beginUpdate`/`endUpdate` 合并刷新，保证最终值、避免循环依赖（嵌套上限兜底）。

## 6. 后台结果

```java
int token = alive;
Thread t = new Thread(() -> {
    int ok = store.save(draft);
    Application.invokeLater(() -> {
        if (alive == 0 || window.disposed != 0) {
            return; // 晚到结果不碰已销毁控件
        }
        // 更新 UI
    });
});
```

## 7. 完整示例

商品编辑器：`examples/product-editor/ProductEditor.java`  
活动监视器轻量状态：页面 / 搜索 / 选中 / 暂停 / 工具栏启用（非整图可观察）。

```bash
java -jar target/jnative.jar examples/product-editor/ProductEditor.java \
  -o build/product-editor/ProductEditor --app-type gui -O0
./build/product-editor/ProductEditor --self-test
./build/product-editor/ProductEditor --appearance light
```
