/**
 * Native unit tests for Scheduler shared logic (validation, layout, recurrence).
 */
public class SchedulerLogicTest {
    static int failures;

    public static void main(String[] args) {
        failures = 0;
        testValidation();
        testRecurrence();
        testOverlapLayout();
        if (failures == 0) {
            System.out.println("SchedulerLogicTest PASS");
        } else {
            System.out.println("SchedulerLogicTest FAIL count=" + failures);
        }
    }

    static void assertTrue(String name, int cond) {
        if (cond == 0) {
            System.out.println("FAIL " + name);
            failures = failures + 1;
        } else {
            System.out.println("ok   " + name);
        }
    }

    static void testValidation() {
        CreateEventRequest bad = new CreateEventRequest();
        bad.title = "";
        bad.startTime = 1000;
        bad.endTime = 2000;
        bad.calendarId = 1;
        int blankBad = 0;
        if (EventValidation.validateCreate(bad).length() > 0) {
            blankBad = 1;
        }
        assertTrue("blank title", blankBad);

        CreateEventRequest ok = new CreateEventRequest();
        ok.title = "Meet";
        ok.startTime = 1000;
        ok.endTime = 2000;
        ok.calendarId = 1;
        int validOk = 0;
        if (EventValidation.validateCreate(ok).length() == 0) {
            validOk = 1;
        }
        assertTrue("valid create", validOk);

        CreateEventRequest order = new CreateEventRequest();
        order.title = "Meet";
        order.startTime = 3000;
        order.endTime = 2000;
        order.calendarId = 1;
        int orderBad = 0;
        if (EventValidation.validateCreate(order).length() > 0) {
            orderBad = 1;
        }
        assertTrue("end before start", orderBad);
    }

    static void testRecurrence() {
        EventDto base = new EventDto();
        base.id = 9;
        base.title = "Weekly";
        base.startTime = 0;
        base.endTime = 3600000;
        base.recurrenceRule = "WEEKLY";
        ArrayList<Object> out = RecurrenceIterator.expand(base, 0, 21L * 86400000L);
        int weeklyOk = 0;
        if (out.size() >= 3) {
            weeklyOk = 1;
        }
        assertTrue("weekly expand >= 3", weeklyOk);

        EventDto once = new EventDto();
        once.id = 1;
        once.title = "Once";
        once.startTime = 1000;
        once.endTime = 2000;
        once.recurrenceRule = "";
        ArrayList<Object> one = RecurrenceIterator.expand(once, 0, 5000);
        int onceOk = 0;
        if (one.size() == 1) {
            onceOk = 1;
        }
        assertTrue("none expand 1", onceOk);
    }

    static void testOverlapLayout() {
        ArrayList<EventDto> day = new ArrayList<EventDto>();
        EventDto a = new EventDto();
        a.title = "A";
        a.startTime = 9L * 3600000L;
        a.endTime = 10L * 3600000L;
        EventDto b = new EventDto();
        b.title = "B";
        b.startTime = 9L * 3600000L + 1800000L;
        b.endTime = 11L * 3600000L;
        EventDto c = new EventDto();
        c.title = "C";
        c.startTime = 10L * 3600000L;
        c.endTime = 10L * 3600000L + 1800000L;
        day.add(a);
        day.add(b);
        day.add(c);
        ArrayList<LaidOutEvent> laid = EventLayoutEngine.layoutDay(day);
        int sizeOk = 0;
        if (laid.size() == 3) {
            sizeOk = 1;
        }
        assertTrue("layout 3", sizeOk);
        int maxCol = 0;
        int i = 0;
        while (i < laid.size()) {
            if (laid.get(i).colCount > maxCol) {
                maxCol = laid.get(i).colCount;
            }
            i = i + 1;
        }
        int colOk = 0;
        if (maxCol > 1) {
            colOk = 1;
        }
        assertTrue("overlap columns > 1", colOk);
    }
}
