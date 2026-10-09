/**
 * REST mirrors under /api/... in ErpServer.
 * @author 张晓达
 */
interface ErpAuthApi {
    AuthSessionDto login(LoginRequest request);

    void logout();

    UserDto currentUser();
}

interface ErpMasterApi {
    ArrayList<Object> listPartners(String kind);

    ArrayList<Object> listItems();

    ArrayList<Object> listWarehouses();

    ArrayList<Object> inventoryBalances(long warehouseId);
}

interface ErpPurchasingApi {
    PurchaseOrderDto createPurchaseOrder(long supplierId, ArrayList<Object> lines);

    PurchaseOrderDto getPurchaseOrder(long id);

    PurchaseOrderDto approvePurchaseOrder(long id);

    PostResultDto postGoodsReceipt(PostGoodsReceiptRequest request);
}

interface ErpSalesApi {
    SalesOrderDto createSalesOrder(long customerId, ArrayList<Object> lines);

    SalesOrderDto getSalesOrder(long id);

    SalesOrderDto approveSalesOrder(long id);

    PostResultDto postDelivery(PostDeliveryRequest request);
}

interface ErpDashboardApi {
    DashboardDto dashboard();
}

interface ErpFinanceApi {
    ArrayList<Object> chartOfAccounts();

    ArrayList<Object> journalEntries(long fromDate, long toDate);

    ArrayList<Object> arInvoices();

    ArrayList<Object> apInvoices();

    ArrayList<Object> bankAccounts();

    ArrayList<Object> trialBalance(long fromDate, long toDate);

    ArrayList<Object> generalLedger(long accountId, long fromDate, long toDate, long partnerId);

    ArrayList<Object> arAgeing(long asOf);

    ArrayList<Object> apAgeing(long asOf);
}
