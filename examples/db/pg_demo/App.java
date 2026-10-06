public class App {
    public static void main(String[] args) {
        String url = Env.get("DATABASE_URL");
        if (url == null || url.length() == 0) {
            System.out.println("skip: DATABASE_URL not set");
            return;
        }

        EventLoop loop = new EventLoop();
        DatabaseConfig cfg = new DatabaseConfig();
        cfg.url = url;
        cfg.loop = loop;

        JnFuture dbFuture = Database.connectWithConfig(cfg);
        Database db = (Database)FutureBlocker.blockingGet(dbFuture);
        ArrayList<Object> empty = new ArrayList<Object>();
        DbQueryResult res = (DbQueryResult)FutureBlocker.blockingGet(
                db.query("SELECT 1 AS one", empty));
        if (res.rows.size() > 0) {
            System.out.println("pg_demo ok: " + res.rows.get(0).getString(0));
        } else {
            System.out.println("pg_demo ok: no rows");
        }
    }
}
