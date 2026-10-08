import jnative.webui.*;
import jnative.browser.*;

/**
 * WASM shell — Finance / Manufacturing / Administration (ERP Design System lite).
 * Html.div2 only accepts two children — nest pairwise. No eval.
 */
public class ErpApp {
    public static void main(String[] args) {
        ErpUiStore.init();
        WebUI.mount("#wasm-root", new ErpShell());
        Browser.console().log("JNative ERP WASM shell ready");
    }
}

class ErpUiStore {
    static StringState title;
    static StringState subtitle;
    static StringState menu;
    static StringState panel;
    static IntState loggedIn;

    static void init() {
        title = WebUI.stateString("JNative ERP");
        subtitle = WebUI.stateString("Login: user1@jnative.dev / erp1234");
        menu = WebUI.stateString("Dashboard");
        panel = WebUI.stateString("Select a menu item. Forms render from controlled metadata only.");
        loggedIn = WebUI.stateInt(0);
    }
}

class ErpShell extends WebUiComponent {
    ErpShell() {
    }

    public View render() {
        View header = Html.div2(Html.h1Dynamic(new ErpTitle()), Html.h1Dynamic(new ErpSubtitle()));
        View finTitle = Html.h1Text("Finance");
        View mfgTitle = Html.h1Text("Manufacturing");
        View admTitle = Html.h1Text("Administration");
        View row1 = Html.div2(
                Html.buttonLabel("Chart of Accounts", Html.onClickVoid(new ErpMenuClick("Chart of Accounts", "/api/erp/finance/accounts"))),
                Html.buttonLabel("Journal Entries", Html.onClickVoid(new ErpMenuClick("Journal Entries", "/api/erp/finance/journals")))
        );
        View row2 = Html.div2(
                Html.buttonLabel("Trial Balance", Html.onClickVoid(new ErpMenuClick("Trial Balance", "/api/erp/finance/trialBalance"))),
                Html.buttonLabel("General Ledger", Html.onClickVoid(new ErpMenuClick("General Ledger", "/api/erp/finance/gl")))
        );
        View mrow1 = Html.div2(
                Html.buttonLabel("BOM", Html.onClickVoid(new ErpMenuClick("BOM", "/api/erp/mfg/boms"))),
                Html.buttonLabel("Production Orders", Html.onClickVoid(new ErpMenuClick("Production Orders", "/api/erp/mfg/productionOrders")))
        );
        View mrow2 = Html.div2(
                Html.buttonLabel("Production Issue", Html.onClickVoid(new ErpMenuClick("Production Issue", "/api/erp/mfg/productionOrders"))),
                Html.buttonLabel("Production Receipt", Html.onClickVoid(new ErpMenuClick("Production Receipt", "/api/erp/mfg/productionOrders")))
        );
        View mrow3 = Html.div2(
                Html.buttonLabel("MRP", Html.onClickVoid(new ErpMenuClick("MRP", "/api/erp/mfg/mrp/suggestions"))),
                Html.buttonLabel("Production Cost", Html.onClickVoid(new ErpMenuClick("Production Cost", "/api/erp/mfg/wip")))
        );
        View arow1 = Html.div2(
                Html.buttonLabel("Users", Html.onClickVoid(new ErpMenuClick("Users", "/api/erp/admin/users"))),
                Html.buttonLabel("Roles", Html.onClickVoid(new ErpMenuClick("Roles", "/api/erp/admin/roles")))
        );
        View arow2 = Html.div2(
                Html.buttonLabel("Permissions", Html.onClickVoid(new ErpMenuClick("Permissions", "/api/erp/admin/permissions"))),
                Html.buttonLabel("Custom Fields", Html.onClickVoid(new ErpMenuClick("Custom Fields", "/api/erp/admin/customFields")))
        );
        View arow3 = Html.div2(
                Html.buttonLabel("Form Configuration", Html.onClickVoid(new ErpMenuClick("Form Configuration", "/api/erp/admin/forms"))),
                Html.buttonLabel("Approval Workflows", Html.onClickVoid(new ErpMenuClick("Approval Workflows", "/api/erp/admin/workflows")))
        );
        View arow4 = Html.div2(
                Html.buttonLabel("Reports", Html.onClickVoid(new ErpMenuClick("Reports", "/api/erp/admin/reports"))),
                Html.buttonLabel("Import / Export", Html.onClickVoid(new ErpMenuClick("Import / Export", "/api/erp/admin/importExport")))
        );
        View arow5 = Html.div2(
                Html.buttonLabel("System Settings", Html.onClickVoid(new ErpMenuClick("System Settings", "/api/erp/admin/settings"))),
                Html.buttonLabel("Audit Logs", Html.onClickVoid(new ErpMenuClick("Audit Logs", "/api/erp/admin/audit")))
        );
        View finance = Html.div2(finTitle, Html.div2(row1, row2));
        View mfg = Html.div2(mfgTitle, Html.div2(Html.div2(mrow1, mrow2), mrow3));
        View admin = Html.div2(admTitle, Html.div2(Html.div2(Html.div2(arow1, arow2), Html.div2(arow3, arow4)), arow5));
        View status = Html.div2(Html.h1Dynamic(new ErpMenuLabel()), Html.h1Dynamic(new ErpPanelLabel()));
        return Html.div2(Html.div2(header, Html.div2(Html.div2(finance, mfg), admin)), status);
    }
}

class ErpTitle implements StringProducer {
    public String produce() {
        return ErpUiStore.title.get();
    }
}

class ErpSubtitle implements StringProducer {
    public String produce() {
        return ErpUiStore.subtitle.get() + " · loggedIn=" + String.valueOf(ErpUiStore.loggedIn.get());
    }
}

class ErpMenuLabel implements StringProducer {
    public String produce() {
        return "Menu: " + ErpUiStore.menu.get();
    }
}

class ErpPanelLabel implements StringProducer {
    public String produce() {
        return ErpUiStore.panel.get();
    }
}

class ErpMenuClick implements VoidCallback {
    String label;
    String path;

    ErpMenuClick(String label, String path) {
        this.label = label;
        this.path = path;
    }

    public void run() {
        ErpUiStore.menu.set(label);
        ErpUiStore.panel.set("GET " + path + " · server re-validates field/RBAC · no eval");
    }
}
