/**
 * Sequential approval workflow. Running instances bind to workflow_versions snapshot.
 * Approval does NOT update business rows directly — caller invokes domain transition.
 * @author 张晓达
 */
class ErpPlatformWorkflow {
    static long defineWorkflow(long companyId, String code, String name, String entityType, String definitionJson) {
        ErpDb.exec(
                "INSERT INTO workflow_definitions(company_id, workflow_code, name, entity_type, active) VALUES ("
                        + CryptoParse.longToString(companyId) + ",'" + code + "','"
                        + ErpPlatformMeta.escapeLite(name) + "','" + entityType + "',1)",
                new ArrayList<Object>());
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(code);
        Object wid = ErpDb.queryOne(
                "SELECT id FROM workflow_definitions WHERE company_id=? AND workflow_code=?", p, new LongMapper());
        long workflowId = ErpNum.asLong(wid);
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO workflow_versions(company_id, workflow_id, version_no, definition_json, created_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(workflowId) + ",1,'"
                        + ErpPlatformMeta.escapeLite(definitionJson) + "',"
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        return workflowId;
    }

    static long startInstance(long companyId, String workflowCode, String entityType, long entityId, long amount,
            long userId, int stepCount, String assigneeRole) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(companyId));
        p.add(workflowCode);
        Object wid = ErpDb.queryOne(
                "SELECT id FROM workflow_definitions WHERE company_id=? AND workflow_code=? AND active=1", p,
                new LongMapper());
        if (wid == null) {
            throw new TransactionException("workflow missing");
        }
        ArrayList<Object> vp = new ArrayList<Object>();
        vp.add(CryptoParse.longToString(ErpNum.asLong(wid)));
        Object vid = ErpDb.queryOne(
                "SELECT id FROM workflow_versions WHERE workflow_id=? ORDER BY version_no DESC LIMIT 1", vp,
                new LongMapper());
        long versionId = ErpNum.asLong(vid);
        long now = AsyncClockNative.wallMillis();
        ErpDb.exec(
                "INSERT INTO workflow_instances(company_id, workflow_version_id, entity_type, entity_id, status, current_step, amount_snapshot, created_by, created_at, updated_at) VALUES ("
                        + CryptoParse.longToString(companyId) + ","
                        + CryptoParse.longToString(versionId) + ",'"
                        + entityType + "',"
                        + CryptoParse.longToString(entityId) + ",'"
                        + WorkflowStatus.IN_APPROVAL + "',1,"
                        + CryptoParse.longToString(amount) + ","
                        + CryptoParse.longToString(userId) + ","
                        + CryptoParse.longToString(now) + ","
                        + CryptoParse.longToString(now) + ")",
                new ArrayList<Object>());
        ArrayList<Object> ip = new ArrayList<Object>();
        ip.add(CryptoParse.longToString(companyId));
        ip.add(CryptoParse.longToString(entityId));
        ip.add(entityType);
        Object iid = ErpDb.queryOne(
                "SELECT id FROM workflow_instances WHERE company_id=? AND entity_id=? AND entity_type=? ORDER BY id DESC LIMIT 1",
                ip, new LongMapper());
        long instanceId = ErpNum.asLong(iid);
        int step = 1;
        while (step <= stepCount) {
            ErpDb.exec(
                    "INSERT INTO workflow_tasks(company_id, instance_id, step_no, assignee_role, assignee_user_id, status, action, comment_text, acted_by, acted_at) VALUES ("
                            + CryptoParse.longToString(companyId) + ","
                            + CryptoParse.longToString(instanceId) + ","
                            + CryptoParse.longToString(step) + ",'"
                            + assigneeRole + "',0,'"
                            + WorkflowTaskStatus.PENDING + "','','',0,0)",
                    new ArrayList<Object>());
            step = step + 1;
        }
        return instanceId;
    }

    /**
     * Approve current step. Returns:
     * 1 = still in approval, 2 = fully approved (caller must run business transition), 0 = rejected/error
     */
    static int act(long companyId, long instanceId, long actorUserId, String action, String comment, long amountNow) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(instanceId));
        p.add(CryptoParse.longToString(companyId));
        DbQueryResult r = ErpDb.query(
                "SELECT status, current_step, amount_snapshot FROM workflow_instances WHERE id=? AND company_id=?",
                p);
        if (r.rows.size() == 0) {
            throw new TransactionException("instance missing");
        }
        Row row = r.rows.get(0);
        String status = row.getString(0);
        int step = row.getInt(1);
        long snap = row.getLong(2);
        if (status.equals(WorkflowStatus.IN_APPROVAL) == false) {
            throw new TransactionException("not in approval");
        }
        if (snap != amountNow) {
            throw new TransactionException("amount changed during approval");
        }
        ArrayList<Object> tp = new ArrayList<Object>();
        tp.add(CryptoParse.longToString(instanceId));
        tp.add(CryptoParse.longToString(companyId));
        tp.add(CryptoParse.longToString(step));
        DbQueryResult tr = ErpDb.query(
                "SELECT id, status, assignee_role FROM workflow_tasks WHERE instance_id=? AND company_id=? AND step_no=?",
                tp);
        if (tr.rows.size() == 0) {
            throw new TransactionException("task missing");
        }
        Row trow = tr.rows.get(0);
        if (trow.getString(1).equals(WorkflowTaskStatus.PENDING) == false) {
            throw new TransactionException("duplicate approval");
        }
        String role = trow.getString(2);
        if (role.length() > 0) {
            /* role-gated: actor must hold ADMIN.WORKFLOW.MANAGE or matching role via permission proxy */
            if (ErpPlatformAuth.hasPermission(actorUserId, companyId, "ADMIN.WORKFLOW.MANAGE") == 0
                    && ErpPlatformAuth.hasPermission(actorUserId, companyId, "SALES.ORDER.APPROVE") == 0) {
                throw new TransactionException("FORBIDDEN:approval");
            }
        }
        long now = AsyncClockNative.wallMillis();
        long taskId = trow.getLong(0);
        ErpDb.exec(
                "UPDATE workflow_tasks SET status='" + WorkflowTaskStatus.DONE + "', action='" + action
                        + "', comment_text='" + ErpPlatformMeta.escapeLite(comment) + "', acted_by="
                        + CryptoParse.longToString(actorUserId) + ", acted_at="
                        + CryptoParse.longToString(now) + " WHERE id=" + CryptoParse.longToString(taskId)
                        + " AND company_id=" + CryptoParse.longToString(companyId)
                        + " AND status='" + WorkflowTaskStatus.PENDING + "'",
                new ArrayList<Object>());
        if (action.equals("REJECT")) {
            ErpDb.exec(
                    "UPDATE workflow_instances SET status='" + WorkflowStatus.REJECTED + "', updated_at="
                            + CryptoParse.longToString(now) + " WHERE id="
                            + CryptoParse.longToString(instanceId),
                    new ArrayList<Object>());
            return 0;
        }
        ArrayList<Object> np = new ArrayList<Object>();
        np.add(CryptoParse.longToString(instanceId));
        np.add(CryptoParse.longToString(companyId));
        np.add(CryptoParse.longToString(step + 1));
        Object next = ErpDb.queryOne(
                "SELECT id FROM workflow_tasks WHERE instance_id=? AND company_id=? AND step_no=?", np,
                new LongMapper());
        if (next == null) {
            ErpDb.exec(
                    "UPDATE workflow_instances SET status='" + WorkflowStatus.APPROVED + "', updated_at="
                            + CryptoParse.longToString(now) + " WHERE id="
                            + CryptoParse.longToString(instanceId),
                    new ArrayList<Object>());
            return 2;
        }
        ErpDb.exec(
                "UPDATE workflow_instances SET current_step=" + CryptoParse.longToString(step + 1)
                        + ", updated_at=" + CryptoParse.longToString(now) + " WHERE id="
                        + CryptoParse.longToString(instanceId),
                new ArrayList<Object>());
        return 1;
    }

    static String instanceStatus(long companyId, long instanceId) {
        ArrayList<Object> p = new ArrayList<Object>();
        p.add(CryptoParse.longToString(instanceId));
        p.add(CryptoParse.longToString(companyId));
        Object v = ErpDb.queryOne(
                "SELECT status FROM workflow_instances WHERE id=? AND company_id=?", p, new StringMapper());
        if (v == null) {
            return "";
        }
        return (String) v;
    }
}
