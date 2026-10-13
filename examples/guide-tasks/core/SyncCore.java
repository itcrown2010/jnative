/**
 * Shared sync protocol rules for guide-tasks (WASM-safe, no lambdas).
 *
 * Owns: sync state machine, queue merge decisions, conflict classification.
 * Does NOT own network or IndexedDB — host adapters call these decisions.
 *
 * Op types: CREATE=1 UPDATE=2 DELETE=3
 * Sync states: LOCAL=1 PENDING=2 SYNCING=3 SYNCED=4 CONFLICT=5 FAILED=6
 * Merge: APPEND=1 REPLACE_LAST=2 DROP_BOTH=3 DROP_NEW=4 REJECT=5
 * Error class: TEMP=1 AUTH=2 VALIDATION=3 CONFLICT=4 PERM=5
 *
 * @author 张晓达
 */
public class SyncCore {
    public static final int OP_CREATE = 1;
    public static final int OP_UPDATE = 2;
    public static final int OP_DELETE = 3;

    public static final int ST_LOCAL = 1;
    public static final int ST_PENDING = 2;
    public static final int ST_SYNCING = 3;
    public static final int ST_SYNCED = 4;
    public static final int ST_CONFLICT = 5;
    public static final int ST_FAILED = 6;

    public static final int MERGE_APPEND = 1;
    public static final int MERGE_REPLACE_LAST = 2;
    public static final int MERGE_DROP_BOTH = 3;
    public static final int MERGE_DROP_NEW = 4;
    public static final int MERGE_REJECT = 5;

    public static final int ERR_TEMP = 1;
    public static final int ERR_AUTH = 2;
    public static final int ERR_VALIDATION = 3;
    public static final int ERR_CONFLICT = 4;
    public static final int ERR_PERM = 5;

    public static int lastError;

    public static int getLastError() {
        return lastError;
    }

    /**
     * Decide how to place a new op given the last pending op for the same task.
     * lastType=0 means no pending ops.
     * createStillLocal=1 when a CREATE for this task has never been acked by server.
     *
     * Rules (must not change net result incorrectly):
     * - no pending → APPEND
     * - CREATE + UPDATE → REPLACE_LAST (latest payload on create)
     * - CREATE + DELETE (never synced) → DROP_BOTH (net zero)
     * - UPDATE + UPDATE → REPLACE_LAST
     * - UPDATE + DELETE → REPLACE_LAST (delete wins, keeps expectedVersion of delete caller)
     * - DELETE + * → DROP_NEW
     * - CREATE after synced DELETE path uses APPEND via lastType=0
     */
    public static int mergeDecision(int lastType, int newType, int createStillLocal) {
        lastError = 0;
        if (newType != OP_CREATE && newType != OP_UPDATE && newType != OP_DELETE) {
            lastError = 1;
            return MERGE_REJECT;
        }
        if (lastType == 0) {
            return MERGE_APPEND;
        }
        if (lastType == OP_DELETE) {
            return MERGE_DROP_NEW;
        }
        if (lastType == OP_CREATE) {
            if (newType == OP_UPDATE) {
                return MERGE_REPLACE_LAST;
            }
            if (newType == OP_DELETE) {
                if (createStillLocal != 0) {
                    return MERGE_DROP_BOTH;
                }
                return MERGE_REPLACE_LAST;
            }
            if (newType == OP_CREATE) {
                lastError = 1;
                return MERGE_REJECT;
            }
        }
        if (lastType == OP_UPDATE) {
            if (newType == OP_UPDATE || newType == OP_DELETE) {
                return MERGE_REPLACE_LAST;
            }
            if (newType == OP_CREATE) {
                lastError = 1;
                return MERGE_REJECT;
            }
        }
        lastError = 1;
        return MERGE_REJECT;
    }

    /** After successful local write of mutation: always PENDING (local saved + queued). */
    public static int stateAfterLocalMutation() {
        lastError = 0;
        return ST_PENDING;
    }

    public static int stateBeginSync(int current) {
        lastError = 0;
        if (current == ST_PENDING || current == ST_FAILED) {
            return ST_SYNCING;
        }
        if (current == ST_SYNCING) {
            return ST_SYNCING;
        }
        lastError = 1;
        return current;
    }

    public static int stateAfterAck() {
        lastError = 0;
        return ST_SYNCED;
    }

    public static int stateAfterConflict() {
        lastError = 0;
        return ST_CONFLICT;
    }

    public static int stateAfterTempFail() {
        lastError = 0;
        return ST_FAILED;
    }

    /**
     * Version check: localExpected must equal serverVersion to apply update/delete.
     * Returns 1 if OK, 0 if conflict.
     */
    public static int versionMatches(int expectedVersion, int serverVersion) {
        lastError = 0;
        if (expectedVersion == serverVersion) {
            return 1;
        }
        return 0;
    }

    /**
     * Delete vs edit conflict: local wants DELETE at expected V, server has version>V or not deleted.
     * Returns 1 if conflict (do not silent-drop).
     */
    public static int isDeleteEditConflict(int opType, int expectedVersion, int serverVersion, int serverDeleted) {
        lastError = 0;
        if (opType == OP_DELETE) {
            if (serverDeleted != 0 && expectedVersion == serverVersion) {
                return 0;
            }
            if (serverDeleted == 0 && expectedVersion != serverVersion) {
                return 1;
            }
            if (serverDeleted == 0 && expectedVersion == serverVersion) {
                return 0;
            }
            return 1;
        }
        if (opType == OP_UPDATE || opType == OP_CREATE) {
            if (serverDeleted != 0) {
                return 1;
            }
            if (versionMatches(expectedVersion, serverVersion) == 0) {
                return 1;
            }
        }
        return 0;
    }

    /** Map HTTP/status codes to error class. bodyCode: 0 none, 1 conflict, 2 validation. */
    public static int classifyHttpError(int httpStatus, int bodyCode) {
        lastError = 0;
        if (bodyCode == 1 || httpStatus == 409) {
            return ERR_CONFLICT;
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return ERR_AUTH;
        }
        if (httpStatus == 400 || httpStatus == 422 || bodyCode == 2) {
            return ERR_VALIDATION;
        }
        if (httpStatus == 408 || httpStatus == 429 || httpStatus == 500 || httpStatus == 502 || httpStatus == 503 || httpStatus == 504) {
            return ERR_TEMP;
        }
        if (httpStatus >= 400 && httpStatus < 500) {
            return ERR_PERM;
        }
        if (httpStatus >= 500) {
            return ERR_TEMP;
        }
        return ERR_TEMP;
    }

    /** Backoff ms for attempt (1-based), capped. */
    public static int backoffMs(int attempt) {
        lastError = 0;
        if (attempt <= 1) {
            return 500;
        }
        if (attempt == 2) {
            return 1000;
        }
        if (attempt == 3) {
            return 2000;
        }
        if (attempt == 4) {
            return 4000;
        }
        return 8000;
    }

    public static int maxAttempts() {
        return 5;
    }

    public static void main(String[] args) {
        boolean keep = false;
        if (keep) {
            lastError = mergeDecision(0, OP_CREATE, 1);
            lastError = mergeDecision(OP_CREATE, OP_UPDATE, 1);
            lastError = mergeDecision(OP_CREATE, OP_DELETE, 1);
            lastError = mergeDecision(OP_UPDATE, OP_UPDATE, 0);
            lastError = mergeDecision(OP_UPDATE, OP_DELETE, 0);
            lastError = mergeDecision(OP_DELETE, OP_UPDATE, 0);
            lastError = stateAfterLocalMutation();
            lastError = stateBeginSync(ST_PENDING);
            lastError = stateAfterAck();
            lastError = stateAfterConflict();
            lastError = stateAfterTempFail();
            lastError = versionMatches(1, 1);
            lastError = isDeleteEditConflict(OP_DELETE, 1, 2, 0);
            lastError = classifyHttpError(503, 0);
            lastError = backoffMs(3);
            lastError = maxAttempts();
            lastError = getLastError();
        }
    }
}
