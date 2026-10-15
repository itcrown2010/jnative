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
    String itemName;
    String uom;
    int quantity;
    int fulfilledQty;
    int returnedQty;
    int reservedQty;
    int openQty;
    int shortfallQty;
    long unitPriceCents;
    /** Basis points: 1300 = 13%. */
    int discountBps;
    int taxBps;
    long lineAmountCents;
}

class PurchaseOrderDto {
    long id;
    String docNo;
    String status;
    long supplierId;
    String supplierName;
    long warehouseId;
    long orderDate;
    long expectedDate;
    String notes;
    String currency;
    String contactName;
    long createdAt;
    ArrayList<Object> lines;
}

class SalesOrderDto {
    long id;
    String docNo;
    /** Compat composite (approval + fulfillment) for existing list UI. */
    String status;
    String approvalStatus;
    String fulfillmentStatus;
    long customerId;
    String customerName;
    long warehouseId;
    long orderDate;
    long deliveryDate;
    String notes;
    String currency;
    String contactName;
    long createdAt;
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
    /** Reserved qty — always 0 until reservation is implemented. */
    int quantityReserved;
    int quantityAvailable;
    int reservedSupported;
    String warehouseCode;
    String warehouseName;
    String itemSku;
    String itemName;
    long valueAmount;
    int minQty;
    /** OK | PENDING_COST | VALUE_WITHOUT_QTY | ZERO_COST */
    String costState;
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
    String normalBalance;
    int isControl;
    int requirePartner;
}

class GlPostingEventDto {
    long id;
    String eventType;
    String sourceType;
    long sourceId;
    long sourceLineId;
    String status;
    long journalEntryId;
    long amount;
    long partnerId;
    long warehouseId;
    long bizDate;
    String errorCode;
    String errorMessage;
}

class GlReconCheckDto {
    String checkName;
    int ok;
    long expectedAmount;
    long actualAmount;
    long diffAmount;
    String detail;
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

class PostingRequest {
    String sourceType;
    long sourceId;
    long postingDate;
    long documentDate;
    String description;
    String idempotencyKey;
    /** DRAFT / APPROVED / POSTED; empty defaults to POSTED (legacy). */
    String initialStatus;
    String eventType;
    ArrayList<Object> lines;
}

class PostingLineInput {
    long accountId;
    long debitAmount;
    long creditAmount;
    long businessPartnerId;
    long warehouseId;
    String description;
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
    long adjustedAmount;
    String status;
    String settlementStatus;
    String currency;
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
    long adjustedAmount;
    String status;
    String settlementStatus;
    String currency;
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
    String paymentMethod;
    String externalRef;
    String notes;
    /** 0=draft then post via separate call; 1=create+post in one step (default for legacy). */
    int postNow;
    ArrayList<Object> allocations;
}

class OutgoingPaymentRequest {
    long supplierId;
    long paymentDate;
    long amount;
    long bankAccountId;
    String idempotencyKey;
    String paymentMethod;
    String externalRef;
    String notes;
    int postNow;
    ArrayList<Object> allocations;
}

class SettlementAllocateRequest {
    long paymentId;
    String idempotencyKey;
    String reason;
    ArrayList<Object> allocations;
}

class SettlementReverseRequest {
    long allocationId;
    String idempotencyKey;
    String reason;
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
