# REST / JSON APIs

**Status:** Experimental

Pattern: route + `JsonCodec` + `ctx.jsonValue(...)` as in `examples/web/hello/App.java`.

`HttpResponse.json` alone does **not** serialize objects—encode explicitly.
