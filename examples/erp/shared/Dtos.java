/**
 * @author 张晓达
 */
class CompanyDto {
    long id;
    String code;
    String name;
}

class UserDto {
    long id;
    long companyId;
    String email;
    String displayName;
    String role;
}

class AuthSessionDto {
    UserDto user;
    String csrfToken;
}

class LoginRequest {
    String email;
    String password;
}

class BusinessPartnerDto {
    long id;
    String code;
    String name;
    String kind;
}

class ItemDto {
    long id;
    String sku;
    String name;
    long unitPriceCents;
}

class WarehouseDto {
    long id;
    String code;
    String name;
}

class OrderLineDto {
    long id;
    long itemId;
    String itemSku;
    int quantity;
    int fulfilledQty;
    long unitPriceCents;
}

class PurchaseOrderDto {
    long id;
    String docNo;
    String status;
    long supplierId;
    String supplierName;
    ArrayList<Object> lines;
}

class SalesOrderDto {
    long id;
    String docNo;
    String status;
    long customerId;
    String customerName;
    ArrayList<Object> lines;
}

class GoodsReceiptDto {
    long id;
    String docNo;
    long purchaseOrderId;
    String status;
    ArrayList<Object> lines;
}

class DeliveryDto {
    long id;
    String docNo;
    long salesOrderId;
    String status;
    ArrayList<Object> lines;
}

class InventoryBalanceDto {
    long warehouseId;
    long itemId;
    int quantityOnHand;
}

class PostLineRequest {
    long orderLineId;
    int quantity;
}

class PostGoodsReceiptRequest {
    long purchaseOrderId;
    long warehouseId;
    ArrayList<Object> lines;
    String idempotencyKey;
}

class PostDeliveryRequest {
    long salesOrderId;
    long warehouseId;
    ArrayList<Object> lines;
    String idempotencyKey;
}

class PostResultDto {
    int ok;
    String code;
    String message;
    String documentJson;
}

class DashboardDto {
    int customerCount;
    int supplierCount;
    int itemCount;
    int openPoCount;
    int openSoCount;
}

class GlAccountDto {
    long id;
    String accountCode;
    String accountName;
    String accountType;
    long parentAccountId;
    int postingAllowed;
    int active;
}

class FiscalPeriodDto {
    long id;
    int fiscalYear;
    int periodNumber;
    long startDate;
    long endDate;
    String status;
}

class JournalLineDto {
    long accountId;
    String accountCode;
    long debitAmount;
    long creditAmount;
    long businessPartnerId;
    String description;
}

class JournalEntryDto {
    long id;
    String journalNumber;
    long postingDate;
    String sourceType;
    long sourceId;
    String status;
    String description;
    long totalDebit;
    long totalCredit;
    ArrayList<Object> lines;
}

class PostingLineInput {
    long accountId;
    long debitAmount;
    long creditAmount;
    long businessPartnerId;
    String description;
}

class PostingRequest {
    String sourceType;
    long sourceId;
    long postingDate;
    long documentDate;
    String description;
    String idempotencyKey;
    ArrayList<Object> lines;
}

class ArInvoiceDto {
    long id;
    long customerId;
    String invoiceNumber;
    long invoiceDate;
    long dueDate;
    long totalAmount;
    long paidAmount;
    long openAmount;
    String status;
}

class ApInvoiceDto {
    long id;
    long supplierId;
    String invoiceNumber;
    long invoiceDate;
    long dueDate;
    long totalAmount;
    long paidAmount;
    long openAmount;
    String status;
}

class PaymentAllocationInput {
    long invoiceId;
    long allocatedAmount;
}

class IncomingPaymentRequest {
    long customerId;
    long paymentDate;
    long amount;
    long bankAccountId;
    String idempotencyKey;
    ArrayList<Object> allocations;
}

class OutgoingPaymentRequest {
    long supplierId;
    long paymentDate;
    long amount;
    long bankAccountId;
    String idempotencyKey;
    ArrayList<Object> allocations;
}

class TrialBalanceRowDto {
    String accountCode;
    String accountName;
    long openingDebit;
    long openingCredit;
    long periodDebit;
    long periodCredit;
    long closingDebit;
    long closingCredit;
}

class AgeingBucketDto {
    long partnerId;
    String partnerName;
    long currentAmount;
    long d1to30;
    long d31to60;
    long d61to90;
    long d90plus;
    long totalOpen;
}

class FinanceScenarioResult {
    int ok;
    long inventoryQty;
    long inventoryValue;
    long cogsAmount;
    long revenueAmount;
    long grossProfit;
    long arOpen;
    long apOpen;
    long trialDebit;
    long trialCredit;
}

class BomLineDto {
    long id;
    long componentItemId;
    String componentSku;
    int quantity;
    String uom;
    int scrapRateBp;
    long warehouseId;
    int sequence;
}

class BomDto {
    long id;
    long parentItemId;
    String parentSku;
    String bomCode;
    int version;
    int baseQuantity;
    String status;
    long effectiveFrom;
    long effectiveTo;
    ArrayList<Object> lines;
}

class BomExplosionLine {
    long componentItemId;
    String componentSku;
    int requiredQuantity;
    int level;
}

class ProductionComponentDto {
    long componentItemId;
    String componentSku;
    int requiredQuantity;
    int issuedQuantity;
    int returnedQuantity;
    long warehouseId;
}

class ProductionOrderDto {
    long id;
    String orderNumber;
    long itemId;
    String itemSku;
    long bomId;
    int bomVersion;
    int plannedQuantity;
    int completedQuantity;
    int scrappedQuantity;
    String status;
    long warehouseId;
    ArrayList<Object> components;
}

class ProductionIssueRequest {
    long productionOrderId;
    long warehouseId;
    long postingDate;
    String idempotencyKey;
    ArrayList<Object> lines;
}

class ProductionIssueLineInput {
    long componentItemId;
    int quantity;
}

class ProductionReceiptRequest {
    long productionOrderId;
    long warehouseId;
    int quantity;
    long postingDate;
    String idempotencyKey;
}

class WipDto {
    long productionOrderId;
    long materialCost;
    long laborCost;
    long overheadCost;
    long transferredCost;
    long remainingCost;
}

class MrpRunRequest {
    String runCode;
    long planningStartDate;
    long planningEndDate;
}

class MrpSuggestionDto {
    long id;
    long mrpRunId;
    String suggestionKind;
    long itemId;
    String itemSku;
    int requiredQuantity;
    long requiredDate;
    long suggestedOrderDate;
    String status;
}

class ManufacturingScenarioResult {
    int ok;
    int motorReq;
    int sensorReq;
    int controllerReq;
    int caseReq;
    int fgQty;
    long wipRemaining;
}
