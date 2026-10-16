/**
 * @author 张晓达
 */
class DocStatus {
    static String DRAFT = "DRAFT";
    static String SUBMITTED = "SUBMITTED";
    static String APPROVED = "APPROVED";
    static String PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";
    static String RECEIVED = "RECEIVED";
    static String PARTIALLY_DELIVERED = "PARTIALLY_DELIVERED";
    static String DELIVERED = "DELIVERED";
    static String CLOSED = "CLOSED";
    static String CANCELLED = "CANCELLED";
}

/** Sales order approval lifecycle (not fulfillment / payment). */
class ApprovalStatus {
    static String DRAFT = "DRAFT";
    static String SUBMITTED = "SUBMITTED";
    static String APPROVED = "APPROVED";
    static String CANCELLED = "CANCELLED";
}

/** Sales order shipment fulfillment (not approval / payment). */
class FulfillmentStatus {
    static String NONE = "NONE";
    static String PARTIAL = "PARTIAL";
    static String FULFILLED = "FULFILLED";
    static String CLOSED = "CLOSED";
}

class ReservationStatus {
    static String ACTIVE = "ACTIVE";
    static String RELEASED = "RELEASED";
    static String CONSUMED = "CONSUMED";
}

class PartnerKind {
    static String CUSTOMER = "CUSTOMER";
    static String SUPPLIER = "SUPPLIER";
    static String BOTH = "BOTH";
}

class InvTxnType {
    static String GOODS_RECEIPT = "GOODS_RECEIPT";
    static String DELIVERY = "DELIVERY";
    static String ADJUSTMENT = "ADJUSTMENT";
    static String SALES_RETURN = "SALES_RETURN";
    static String PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    static String PRODUCTION_RETURN = "PRODUCTION_RETURN";
    static String PRODUCTION_RECEIPT = "PRODUCTION_RECEIPT";
}

class DocRefKind {
    static String GR_TO_PO = "GR_TO_PO";
    static String DL_TO_SO = "DL_TO_SO";
    static String SR_TO_DL = "SR_TO_DL";
}

class AccountType {
    static String ASSET = "ASSET";
    static String LIABILITY = "LIABILITY";
    static String EQUITY = "EQUITY";
    static String REVENUE = "REVENUE";
    static String EXPENSE = "EXPENSE";
}

class FiscalPeriodStatus {
    static String OPEN = "OPEN";
    static String CLOSING = "CLOSING";
    static String CLOSED = "CLOSED";
}

class JournalStatus {
    static String DRAFT = "DRAFT";
    /** Reviewed; not yet in books. */
    static String APPROVED = "APPROVED";
    /** Entered books; trial balance / GL include only this. */
    static String POSTED = "POSTED";
    static String REVERSED = "REVERSED";
    static String REJECTED = "REJECTED";
}

/** Company GL posting policy (management accounting). */
class GlPolicy {
    /** Existing GR→Inv/GR_CLEARING + DL COGS; formal AP confirm PENDING_CONFIG. */
    static String PROVISIONAL_CLEARING = "PROVISIONAL_CLEARING";
    /** Test/mgmt: purchase Dr Inv Cr AP; sale Dr AR Cr Rev + COGS; cash Dr/Cr bank. No tax. */
    static String MGMT_DIRECT_ACCRUAL = "MGMT_DIRECT_ACCRUAL";
}

class GlEventType {
    static String OPENING_CASH = "OPENING_CASH";
    static String PURCHASE_STOCK = "PURCHASE_STOCK";
    static String SALE_REVENUE = "SALE_REVENUE";
    static String SALE_COGS = "SALE_COGS";
    static String CUSTOMER_RECEIPT = "CUSTOMER_RECEIPT";
    static String VENDOR_PAYMENT = "VENDOR_PAYMENT";
    static String REVERSAL = "REVERSAL";
}

class GlEventStatus {
    static String PENDING = "PENDING";
    static String POSTED = "POSTED";
    static String FAILED = "FAILED";
    static String SKIPPED = "SKIPPED";
}

class NormalBalance {
    static String DEBIT = "DEBIT";
    static String CREDIT = "CREDIT";
}

class InvoiceStatus {
    static String DRAFT = "DRAFT";
    /** GR-created provisional supplier AP — not vendor invoice confirmation. */
    static String PROVISIONAL = "PROVISIONAL";
    static String POSTED = "POSTED";
    static String PARTIALLY_PAID = "PARTIALLY_PAID";
    static String PAID = "PAID";
    static String CANCELLED = "CANCELLED";
}

class PaymentStatus {
    static String DRAFT = "DRAFT";
    static String POSTED = "POSTED";
    static String CANCELLED = "CANCELLED";
}

/** Settlement of AR/AP open amounts — independent of confirm/provisional status. */
class SettlementStatus {
    static String OPEN = "OPEN";
    static String PARTIAL = "PARTIAL";
    static String SETTLED = "SETTLED";
}

class AllocationRowStatus {
    static String ACTIVE = "ACTIVE";
    static String REVERSED = "REVERSED";
}

class CashMovementType {
    static String RECEIPT = "RECEIPT";
    static String PAYMENT = "PAYMENT";
    static String REFUND_OUT = "REFUND_OUT";
    static String REFUND_IN = "REFUND_IN";
}

class FinanceEventType {
    static String PROVISIONAL_AR = "PROVISIONAL_AR";
    static String PROVISIONAL_AP = "PROVISIONAL_AP";
    static String RECEIPT_POST = "RECEIPT_POST";
    static String PAYMENT_POST = "PAYMENT_POST";
    static String RECEIPT_VOID = "RECEIPT_VOID";
    static String PAYMENT_VOID = "PAYMENT_VOID";
    static String AR_ADJUST = "AR_ADJUST";
    static String AP_ADJUST = "AP_ADJUST";
    static String REFUND = "REFUND";
}

class AccountMapKey {
    static String INVENTORY = "INVENTORY";
    static String GR_CLEARING = "GR_CLEARING";
    static String COGS = "COGS";
    static String REVENUE = "REVENUE";
    static String AR = "AR";
    static String AP = "AP";
    static String BANK = "BANK";
    static String EQUITY = "EQUITY";
    static String WIP = "WIP";
    static String FINISHED_GOODS = "FINISHED_GOODS";
}

class JournalSource {
    static String GOODS_RECEIPT = "GOODS_RECEIPT";
    static String DELIVERY = "DELIVERY";
    static String AR_INVOICE = "AR_INVOICE";
    static String AP_INVOICE = "AP_INVOICE";
    static String INCOMING_PAYMENT = "INCOMING_PAYMENT";
    static String OUTGOING_PAYMENT = "OUTGOING_PAYMENT";
    static String MANUAL = "MANUAL";
    static String REVERSAL = "REVERSAL";
    static String PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    static String PRODUCTION_RETURN = "PRODUCTION_RETURN";
    static String PRODUCTION_RECEIPT = "PRODUCTION_RECEIPT";
    static String GL_EVENT = "GL_EVENT";
    static String OPENING = "OPENING";
}

class BomStatus {
    static String DRAFT = "DRAFT";
    static String ACTIVE = "ACTIVE";
    static String OBSOLETE = "OBSOLETE";
}

class ProductionOrderStatus {
    static String PLANNED = "PLANNED";
    static String RELEASED = "RELEASED";
    static String IN_PROGRESS = "IN_PROGRESS";
    static String PARTIALLY_COMPLETED = "PARTIALLY_COMPLETED";
    static String COMPLETED = "COMPLETED";
    static String CLOSED = "CLOSED";
    static String CANCELLED = "CANCELLED";
}

class MfgDocStatus {
    static String DRAFT = "DRAFT";
    static String POSTED = "POSTED";
    static String CANCELLED = "CANCELLED";
}

class MrpSuggestionKind {
    static String PURCHASE = "PURCHASE";
    static String PRODUCTION = "PRODUCTION";
}

class DataScope {
    static String OWN = "OWN";
    static String DEPARTMENT = "DEPARTMENT";
    static String COMPANY = "COMPANY";
}

class CustomFieldType {
    static String STRING = "STRING";
    static String INTEGER = "INTEGER";
    static String DECIMAL = "DECIMAL";
    static String BOOLEAN = "BOOLEAN";
    static String DATE = "DATE";
    static String DATETIME = "DATETIME";
    static String ENUM = "ENUM";
}

class WorkflowStatus {
    static String DRAFT = "DRAFT";
    static String SUBMITTED = "SUBMITTED";
    static String IN_APPROVAL = "IN_APPROVAL";
    static String APPROVED = "APPROVED";
    static String REJECTED = "REJECTED";
    static String CANCELLED = "CANCELLED";
}

class WorkflowTaskStatus {
    static String PENDING = "PENDING";
    static String DONE = "DONE";
    static String SKIPPED = "SKIPPED";
}

class SettingType {
    static String STRING = "STRING";
    static String INTEGER = "INTEGER";
    static String DECIMAL = "DECIMAL";
    static String BOOLEAN = "BOOLEAN";
    static String DATE = "DATE";
    static String ENUM = "ENUM";
}
