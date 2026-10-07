/**
 * Positions overlapping timed events into columns (week/day view).
 */
class LaidOutEvent {
    EventDto event;
    int col;
    int colCount;
    int topMin;
    int heightMin;

    LaidOutEvent() {
        event = null;
        col = 0;
        colCount = 1;
        topMin = 0;
        heightMin = 30;
    }
}

class EventLayoutEngine {
    static ArrayList<LaidOutEvent> layoutDay(ArrayList<EventDto> events) {
        ArrayList<EventDto> timed = new ArrayList<EventDto>();
        int i = 0;
        while (i < events.size()) {
            EventDto e = events.get(i);
            if (e.allDay == 0) {
                timed.add(e);
            }
            i = i + 1;
        }
        sortByStart(timed);
        ArrayList<LaidOutEvent> out = new ArrayList<LaidOutEvent>();
        ArrayList<EventDto> cluster = new ArrayList<EventDto>();
        i = 0;
        while (i < timed.size()) {
            EventDto e = timed.get(i);
            if (cluster.size() == 0) {
                cluster.add(e);
            } else {
                EventDto last = cluster.get(cluster.size() - 1);
                long clusterEnd = maxEnd(cluster);
                if (e.startTime < clusterEnd) {
                    cluster.add(e);
                } else {
                    packCluster(cluster, out);
                    cluster = new ArrayList<EventDto>();
                    cluster.add(e);
                }
            }
            i = i + 1;
        }
        if (cluster.size() > 0) {
            packCluster(cluster, out);
        }
        return out;
    }

    static long maxEnd(ArrayList<EventDto> cluster) {
        long m = 0;
        int i = 0;
        while (i < cluster.size()) {
            if (cluster.get(i).endTime > m) {
                m = cluster.get(i).endTime;
            }
            i = i + 1;
        }
        return m;
    }

    static void packCluster(ArrayList<EventDto> cluster, ArrayList<LaidOutEvent> out) {
        long[] colEnds = new long[32];
        int colCount = 0;
        int i = 0;
        int clusterStart = out.size();
        while (i < cluster.size()) {
            EventDto e = cluster.get(i);
            int col = 0;
            int found = 0;
            while (col < colCount && found == 0) {
                if (colEnds[col] <= e.startTime) {
                    found = 1;
                    colEnds[col] = e.endTime;
                } else {
                    col = col + 1;
                }
            }
            if (found == 0) {
                if (colCount < 32) {
                    colEnds[colCount] = e.endTime;
                    col = colCount;
                    colCount = colCount + 1;
                } else {
                    col = 0;
                }
            }
            LaidOutEvent lo = new LaidOutEvent();
            lo.event = e;
            lo.col = col;
            lo.colCount = 1;
            lo.topMin = minutesOfDay(e.startTime);
            lo.heightMin = minutesOfDay(e.endTime) - lo.topMin;
            if (lo.heightMin < 20) {
                lo.heightMin = 20;
            }
            out.add(lo);
            i = i + 1;
        }
        int cols = colCount;
        if (cols < 1) {
            cols = 1;
        }
        i = clusterStart;
        while (i < out.size()) {
            out.get(i).colCount = cols;
            i = i + 1;
        }
    }

    static int minutesOfDay(long epochMs) {
        long tod = epochMs % 86400000L;
        if (tod < 0) {
            tod = tod + 86400000L;
        }
        long mins = tod / 60000L;
        int out = 0;
        while (mins > 0L && out < 1440) {
            out = out + 1;
            mins = mins - 1L;
        }
        return out;
    }

    static void sortByStart(ArrayList<EventDto> list) {
        int n = list.size();
        int i = 1;
        while (i < n) {
            EventDto key = list.get(i);
            int j = i - 1;
            int moving = 1;
            while (j >= 0 && moving != 0) {
                if (list.get(j).startTime <= key.startTime) {
                    moving = 0;
                } else {
                    list.set(j + 1, list.get(j));
                    j = j - 1;
                }
            }
            list.set(j + 1, key);
            i = i + 1;
        }
    }
}
