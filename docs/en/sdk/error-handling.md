# SDK error handling

**Status:** Experimental

- IO / network failures surface as exceptions or failed status codes (HTTP).  
- Always check `File.exists` when absence is normal.  
- HTTP: inspect `HttpResponse.statusCode()` / `bodyString()`.  
- Threads: handle join ordering; protect shared state with `Mutex`.

See [../language/errors-and-exceptions.md](../language/errors-and-exceptions.md) · [../troubleshooting/index.md](../troubleshooting/index.md)
