/**
 * In-app Chinese tutorials (practice materials: FG-PRACTICE-A / WH-PRACTICE).
 */
(function (global) {
  global.JnHelpZh = {
    tutorials: [
      { id: "T01", title: "建正式账套", role: "admin", menu: "系统管理 / 首次向导",
        input: "填写公司编码、名称、时区；创建 bookKind=FORMAL 正式账套。",
        expect: "companies 新行 book_kind=FORMAL；无演示业务数据。",
        commonError: "勿把演示库当作正式账套使用。" },
      { id: "T02", title: "初始化向导", role: "admin", menu: "首次向导各步骤",
        input: "币种与精度→仓库→编号规则→管理员用户→主数据→期初→校验完成。",
        expect: "setup_wizard_state.completed=1；companies.setup_completed=1。",
        commonError: "已有业务单据后基础设置会被锁定。" },
      { id: "T03", title: "CSV 导入主数据", role: "admin", menu: "系统管理 / 导入导出",
        input: "下载中文模板；UTF-8 CSV 预览；确认后事务写入。",
        expect: "import_batches 有审计；重复编码默认跳过不覆盖；错误行不写入。",
        commonError: "禁止公式注入单元格未转义再导入。" },
      { id: "T04", title: "期初库存核对", role: "admin/finance", menu: "库存 / 期初库存",
        input: "创建期初库存草稿并过账；禁止直接改余额表。",
        expect: "inventory_balances 增加数量；有 OS 流水；幂等键可回放。",
        commonError: "缺少 Idempotency-Key。" },
      { id: "T05", title: "第一笔购销", role: "purchase/sales", menu: "采购收货 / 销售出库",
        input: "采购入库 10；销售出库 3。使用真实 API。",
        expect: "库存 20→30→27。",
        commonError: "未审批订单不可收货/出库。" },
      { id: "T06", title: "错误单据冲销", role: "admin", menu: "出库详情 / 冲销",
        input: "对已过账出库冲销并填原因；再次冲销应被拒绝。",
        expect: "原单 REVERSED；库存恢复；审计有 REVERSE。",
        commonError: "存在下游 AR/AP 时须先处理。" },
      { id: "T07", title: "备份与恢复", role: "admin", menu: "运维脚本",
        input: "执行 backup-restore.sh；升级前自动 pg_dump。",
        expect: "独立库恢复后计数与 version 一致。",
        commonError: "禁止删库重建当升级。" },
      { id: "T08", title: "演示练习料", role: "任意", menu: "演示账套",
        input: "使用 FG-PRACTICE-A / WH-PRACTICE；可重置演示库。",
        expect: "正式账套演示重置返回 403。",
        commonError: "演示 seed 升级不得覆盖用户业务单据。" }
    ]
  };
})(window);
