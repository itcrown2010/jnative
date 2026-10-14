/**
 * Flattened process-page rows: section headers, expandable app groups, leaf processes.
 * Grouping uses jn_am group_class / win_count / path — not official AUMID.
 * @author 张晓达
 */
class TmViewRow {
    static int KIND_SECTION = 0;
    static int KIND_GROUP = 1;
    static int KIND_PROC = 2;

    int kind;
    String sectionId; /* apps | bg | win */
    String groupKey;
    String title;
    AmProcRow proc;
    ArrayList<AmProcRow> members;
    int expanded;
    int cpuMilli;
    long memBytes;
    int childCount;

    TmViewRow() {
        kind = KIND_PROC;
        sectionId = "";
        groupKey = "";
        title = "";
        proc = null;
        members = null;
        expanded = 1;
        cpuMilli = -1;
        memBytes = -1;
        childCount = 0;
    }

    String rowId() {
        if (kind == KIND_SECTION) {
            return "sec:" + sectionId;
        }
        if (kind == KIND_GROUP) {
            return "grp:" + groupKey;
        }
        if (proc != null && proc.sample != null) {
            return proc.sample.identityKey();
        }
        return "";
    }
}

class TmViewModel {
    static ArrayList<TmViewRow> build(ArrayList<AmProcRow> filtered, int sortCol, int sortAsc,
            HashMap<String, Integer> expandedMap) {
        ArrayList<AmProcRow> apps = new ArrayList<AmProcRow>();
        ArrayList<AmProcRow> bg = new ArrayList<AmProcRow>();
        ArrayList<AmProcRow> win = new ArrayList<AmProcRow>();
        int i = 0;
        while (i < filtered.size()) {
            AmProcRow r = filtered.get(i);
            if (r != null && r.sample != null) {
                int g = r.sample.groupClass;
                if (g == AmGroupClass.APP) {
                    apps.add(r);
                } else if (g == AmGroupClass.WINDOWS) {
                    win.add(r);
                } else {
                    bg.add(r);
                }
            }
            i = i + 1;
        }
        ArrayList<TmViewRow> out = new ArrayList<TmViewRow>();
        appendSection(out, "apps", "应用", apps, sortCol, sortAsc, expandedMap, 1);
        appendSection(out, "bg", "后台进程", bg, sortCol, sortAsc, expandedMap, 0);
        appendSection(out, "win", "Windows 进程", win, sortCol, sortAsc, expandedMap, 0);
        return out;
    }

    static void appendSection(ArrayList<TmViewRow> out, String sid, String label,
            ArrayList<AmProcRow> rows, int sortCol, int sortAsc,
            HashMap<String, Integer> expandedMap, int doGroup) {
        int secExp = 1;
        if (expandedMap != null) {
            Integer v = expandedMap.get("sec:" + sid);
            if (v != null && v.intValue() == 0) {
                secExp = 0;
            }
        }
        TmViewRow sec = new TmViewRow();
        sec.kind = TmViewRow.KIND_SECTION;
        sec.sectionId = sid;
        sec.title = label + " (" + String.valueOf(rows.size()) + ")";
        sec.expanded = secExp;
        sec.childCount = rows.size();
        out.add(sec);
        if (secExp == 0) {
            return;
        }
        if (doGroup != 0) {
            ArrayList<ArrayList<AmProcRow>> groups = groupByPath(rows);
            int gi = 0;
            while (gi < groups.size()) {
                ArrayList<AmProcRow> members = groups.get(gi);
                if (members.size() == 0) {
                    gi = gi + 1;
                    continue;
                }
                if (members.size() == 1) {
                    AmProcRow alone = members.get(0);
                    TmViewRow leaf = leafOf(alone, sid);
                    out.add(leaf);
                } else {
                    AmProcRow head = pickHead(members);
                    String gkey = groupKeyOf(head);
                    int gexp = 1;
                    if (expandedMap != null) {
                        Integer v = expandedMap.get("grp:" + gkey);
                        if (v != null && v.intValue() == 0) {
                            gexp = 0;
                        }
                    }
                    TmViewRow grp = new TmViewRow();
                    grp.kind = TmViewRow.KIND_GROUP;
                    grp.sectionId = sid;
                    grp.groupKey = gkey;
                    grp.proc = head;
                    grp.members = members;
                    grp.expanded = gexp;
                    grp.childCount = members.size();
                    grp.title = head.sample.name;
                    grp.cpuMilli = sumCpu(members);
                    grp.memBytes = sumMem(members);
                    out.add(grp);
                    if (gexp != 0) {
                        ArrayList<AmProcRow> sorted = TmSort.sortRows(members, sortCol, sortAsc);
                        int mi = 0;
                        while (mi < sorted.size()) {
                            TmViewRow child = leafOf(sorted.get(mi), sid);
                            child.groupKey = gkey;
                            out.add(child);
                            mi = mi + 1;
                        }
                    }
                }
                gi = gi + 1;
            }
        } else {
            ArrayList<AmProcRow> sorted = TmSort.sortRows(rows, sortCol, sortAsc);
            int j = 0;
            while (j < sorted.size()) {
                out.add(leafOf(sorted.get(j), sid));
                j = j + 1;
            }
        }
    }

    static TmViewRow leafOf(AmProcRow r, String sid) {
        TmViewRow row = new TmViewRow();
        row.kind = TmViewRow.KIND_PROC;
        row.sectionId = sid;
        row.proc = r;
        row.title = r.sample.name;
        row.cpuMilli = r.cpuMilli;
        row.memBytes = memOf(r);
        return row;
    }

    static long memOf(AmProcRow r) {
        if (r == null || r.sample == null) {
            return -1;
        }
        if ((r.sample.flags & AmFlags.FOOTPRINT) != 0) {
            return r.sample.footprint;
        }
        if ((r.sample.flags & AmFlags.RSS) != 0) {
            return r.sample.rss;
        }
        return -1;
    }

    static int sumCpu(ArrayList<AmProcRow> members) {
        int s = 0;
        int any = 0;
        int i = 0;
        while (i < members.size()) {
            AmProcRow r = members.get(i);
            if (r != null && r.cpuMilli >= 0) {
                s = s + r.cpuMilli;
                any = 1;
            }
            i = i + 1;
        }
        if (any == 0) {
            return -1;
        }
        return s;
    }

    static long sumMem(ArrayList<AmProcRow> members) {
        long s = 0;
        int any = 0;
        int i = 0;
        while (i < members.size()) {
            long m = memOf(members.get(i));
            if (m >= 0) {
                s = s + m;
                any = 1;
            }
            i = i + 1;
        }
        if (any == 0) {
            return -1;
        }
        return s;
    }

    static String groupKeyOf(AmProcRow r) {
        if (r == null || r.sample == null) {
            return "";
        }
        String p = r.sample.path;
        if (p != null && p.length() > 0) {
            return AmParse.trim(p).toLowerCase();
        }
        return "name:" + (r.sample.name != null ? r.sample.name.toLowerCase() : "");
    }

    static AmProcRow pickHead(ArrayList<AmProcRow> members) {
        AmProcRow best = members.get(0);
        int i = 1;
        while (i < members.size()) {
            AmProcRow r = members.get(i);
            if (r.sample.winCount > best.sample.winCount) {
                best = r;
            } else if (r.sample.winCount == best.sample.winCount && r.sample.pid < best.sample.pid) {
                best = r;
            }
            i = i + 1;
        }
        return best;
    }

    static ArrayList<ArrayList<AmProcRow>> groupByPath(ArrayList<AmProcRow> rows) {
        ArrayList<String> keys = new ArrayList<String>();
        ArrayList<ArrayList<AmProcRow>> buckets = new ArrayList<ArrayList<AmProcRow>>();
        int i = 0;
        while (i < rows.size()) {
            AmProcRow r = rows.get(i);
            String k = groupKeyOf(r);
            int found = -1;
            int j = 0;
            while (j < keys.size()) {
                if (keys.get(j).equals(k)) {
                    found = j;
                    break;
                }
                j = j + 1;
            }
            if (found < 0) {
                keys.add(k);
                ArrayList<AmProcRow> b = new ArrayList<AmProcRow>();
                b.add(r);
                buckets.add(b);
            } else {
                buckets.get(found).add(r);
            }
            i = i + 1;
        }
        return buckets;
    }
}

/** Sort helpers for W11 columns: 0 name 1 status 2 cpu 3 mem 4 disk 5 net */
class TmSort {
    static ArrayList<AmProcRow> sortRows(ArrayList<AmProcRow> src, int col, int asc) {
        ArrayList<AmProcRow> out = new ArrayList<AmProcRow>();
        int i = 0;
        while (i < src.size()) {
            out.add(src.get(i));
            i = i + 1;
        }
        heapSort(out, col, asc);
        return out;
    }

    static void heapSort(ArrayList<AmProcRow> a, int col, int asc) {
        int n = a.size();
        if (n < 2) {
            return;
        }
        int i = n / 2 - 1;
        while (i >= 0) {
            heapify(a, n, i, col, asc);
            i = i - 1;
        }
        int end = n - 1;
        while (end > 0) {
            AmProcRow t = a.get(0);
            a.set(0, a.get(end));
            a.set(end, t);
            heapify(a, end, 0, col, asc);
            end = end - 1;
        }
    }

    static void heapify(ArrayList<AmProcRow> a, int n, int i, int col, int asc) {
        for (;;) {
            int l = 2 * i + 1;
            int r = 2 * i + 2;
            int largest = i;
            if (l < n && cmp(a.get(l), a.get(largest), col, asc) > 0) {
                largest = l;
            }
            if (r < n && cmp(a.get(r), a.get(largest), col, asc) > 0) {
                largest = r;
            }
            if (largest == i) {
                return;
            }
            AmProcRow t = a.get(i);
            a.set(i, a.get(largest));
            a.set(largest, t);
            i = largest;
        }
    }

    static int cmp(AmProcRow x, AmProcRow y, int col, int asc) {
        if (x == null || y == null || x.sample == null || y.sample == null) {
            return 0;
        }
        int c = 0;
        if (col == 0) {
            String nx = x.sample.name != null ? x.sample.name : "";
            String ny = y.sample.name != null ? y.sample.name : "";
            c = nx.compareTo(ny);
        } else if (col == 1) {
            c = x.sample.statusBits - y.sample.statusBits;
        } else if (col == 2) {
            int cx = x.cpuMilli < 0 ? -1 : x.cpuMilli;
            int cy = y.cpuMilli < 0 ? -1 : y.cpuMilli;
            c = cx - cy;
        } else if (col == 3) {
            long mx = TmViewModel.memOf(x);
            long my = TmViewModel.memOf(y);
            if (mx < my) {
                c = -1;
            } else if (mx > my) {
                c = 1;
            }
        } else {
            /* disk/net unavailable — stable by name */
            String nx = x.sample.name != null ? x.sample.name : "";
            String ny = y.sample.name != null ? y.sample.name : "";
            c = nx.compareTo(ny);
        }
        if (asc == 0) {
            c = 0 - c;
        }
        return c;
    }
}
