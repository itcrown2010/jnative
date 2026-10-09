/**
 * Typed RPC contracts (shared). Server mounts generated dispatchers when available;
 * SchedulerServer also exposes REST mirrors for browser fetch until WASM RPC client is complete.
 * @author 张晓达
 */
@Rpc("/api/auth")
interface AuthApi {
    AuthSessionDto login(LoginRequest request);

    void logout();

    UserDto currentUser();
}

@Rpc("/api/calendar")
interface CalendarApi {
    ArrayList<CalendarDto> listCalendars();

    ArrayList<EventDto> eventsBetween(long fromMs, long toMs);

    EventDto createEvent(CreateEventRequest request);

    EventDto updateEvent(long id, UpdateEventRequest request);

    void deleteEvent(long id);
}

@Rpc("/api/tasks")
interface TaskApi {
    ArrayList<TaskDto> listTasks();

    TaskDto createTask(CreateTaskRequest request);

    TaskDto updateTask(long id, UpdateTaskRequest request);

    void deleteTask(long id);
}

@Rpc("/api/search")
interface SearchApi {
    ArrayList<SearchResultDto> search(String query);
}
