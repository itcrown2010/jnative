/**
 * @author 张晓达
 */
class DocStatus {
    static String DRAFT = "DRAFT";
    static String APPROVED = "APPROVED";
    static String PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";
    static String RECEIVED = "RECEIVED";
    static String PARTIALLY_DELIVERED = "PARTIALLY_DELIVERED";
    static String DELIVERED = "DELIVERED";
    static String CANCELLED = "CANCELLED";
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
    static String PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    static String PRODUCTION_RETURN = "PRODUCTION_RETURN";
    static String PRODUCTION_RECEIPT = "PRODUCTION_RECEIPT";
}

class DocRefKind {
    static String GR_TO_PO = "GR_TO_PO";
    static String DL_TO_SO = "DL_TO_SO";
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
    static String POSTED = "POSTED";
    static String REVERSED = "REVERSED";
}

class InvoiceStatus {
    static String DRAFT = "DRAFT";
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

class AccountMapKey {
    static String INVENTORY = "INVENTORY";
    static String GR_CLEARING = "GR_CLEARING";
    static String COGS = "COGS";
    static String REVENUE = "REVENUE";
    static String AR = "AR";
    static String AP = "AP";
    static String BANK = "BANK";
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
