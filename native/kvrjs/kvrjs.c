// KVR OS JS engine: QuickJS-ng compiled to WebAssembly (wasi-sdk, reactor) and run on the JVM by
// Chicory. This shim is the whole ABI between Java (script/chicory/ChicoryJsEngine) and QuickJS:
//
//   exports  kvr_alloc(n) -> ptr          kvr_free(ptr)
//            kvr_init(memLimit, stackLimit) -> 0 ok
//            kvr_eval(src, srcLen, name, nameLen) -> 0 ok / 1 error      (src and name NUL-terminated)
//            kvr_call(fn, fnLen, argsJson, argsLen) -> 0 ok / 1 error    (args = JSON array; result JSON)
//            kvr_expose(name, nameLen) -> 0 ok                           (global JS function -> host_call)
//            kvr_out_ptr() / kvr_out_len()   last result (JSON) or error text ("Error: msg\n    at ...")
//            kvr_memory_used() -> bytes
//   imports  kvr.host_call(name, nameLen, argsJson, argsLen) -> len (>= 0 ok, -(len+1) error)
//            kvr.host_result(dst)          copies the pending host result (len bytes) into dst
//            kvr.interrupted() -> 0/1      polled by QuickJS; 1 = watchdog kill (uncatchable)
//
// Values cross the boundary only as UTF-8 JSON: host results are JSON.parse()d here, call results
// JSON.stringify()d. Pending Promise jobs run after every eval/call. No quickjs-libc: scripts get no
// std/os modules, no files, no network — everything goes through kvr.* host functions.

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "quickjs.h"

#define KVR_IMPORT(n) __attribute__((import_module("kvr"), import_name(n)))
#define KVR_EXPORT(n) __attribute__((export_name(n)))

KVR_IMPORT("host_call") int32_t kvr_host_call(const char *name, int32_t name_len, const char *args, int32_t args_len);
KVR_IMPORT("host_result") void kvr_host_result(char *dst);
KVR_IMPORT("interrupted") int32_t kvr_interrupted(void);

#define MAX_ARGS 16
#define MAX_JOBS 100000

static JSRuntime *rt;
static JSContext *ctx;
static char *out;
static int32_t out_len;

// --- result buffer ---------------------------------------------------------------------------

static void set_out(const char *s, size_t len) {
    free(out);
    out = (char *) malloc(len + 1);
    if (!out) {
        out_len = 0;
        return;
    }
    memcpy(out, s, len);
    out[len] = 0;
    out_len = (int32_t) len;
}

static void set_out_str(const char *s) {
    set_out(s, strlen(s));
}

static void set_out_json(JSValueConst v) {
    JSValue json = JS_JSONStringify(ctx, v, JS_UNDEFINED, JS_UNDEFINED);
    if (JS_IsException(json)) {
        JS_FreeValue(ctx, JS_GetException(ctx)); // cyclic or BigInt result: report null, not a crash
        set_out_str("null");
        return;
    }
    size_t len = 0;
    const char *s = JS_IsUndefined(json) ? NULL : JS_ToCStringLen(ctx, &len, json);
    if (s) {
        set_out(s, len);
        JS_FreeCString(ctx, s);
    } else {
        set_out_str("null");
    }
    JS_FreeValue(ctx, json);
}

/** "Error: boom\n    at f (app/main.js:3:9)" — message plus stack when there is one. */
static void set_out_exception(void) {
    JSValue ex = JS_GetException(ctx);
    const char *msg = JS_ToCString(ctx, ex);
    JSValue stack = JS_UNDEFINED;
    if (JS_IsObject(ex)) stack = JS_GetPropertyStr(ctx, ex, "stack");
    const char *st = JS_IsString(stack) ? JS_ToCString(ctx, stack) : NULL;
    size_t a = msg ? strlen(msg) : 0;
    size_t b = st ? strlen(st) : 0;
    char *buf = (char *) malloc(a + b + 2);
    if (buf) {
        if (a) memcpy(buf, msg, a);
        size_t n = a;
        if (b) {
            buf[n++] = '\n';
            memcpy(buf + n, st, b);
            n += b;
        }
        set_out(buf, n);
        free(buf);
    } else {
        set_out_str("out of memory");
    }
    if (msg) JS_FreeCString(ctx, msg);
    if (st) JS_FreeCString(ctx, st);
    JS_FreeValue(ctx, stack);
    JS_FreeValue(ctx, ex);
}

// --- runtime ---------------------------------------------------------------------------------

static int interrupt_handler(JSRuntime *r, void *opaque) {
    (void) r;
    (void) opaque;
    return kvr_interrupted();
}

/** Drain the Promise job queue; -1 if a job threw something uncatchable (interrupt, OOM). */
static int run_jobs(void) {
    JSContext *c;
    for (int i = 0; i < MAX_JOBS; i++) {
        int r = JS_ExecutePendingJob(rt, &c);
        if (r == 0) return 0;
        if (r < 0) return -1;
    }
    return 0;
}

KVR_EXPORT("kvr_alloc") void *kvr_alloc(int32_t n) {
    return malloc(n > 0 ? (size_t) n : 1);
}

KVR_EXPORT("kvr_free") void kvr_free(void *p) {
    free(p);
}

KVR_EXPORT("kvr_out_ptr") char *kvr_out_ptr(void) {
    return out;
}

KVR_EXPORT("kvr_out_len") int32_t kvr_out_len(void) {
    return out_len;
}

KVR_EXPORT("kvr_init") int32_t kvr_init(int32_t memory_limit, int32_t stack_limit) {
    if (rt) return 0;
    rt = JS_NewRuntime();
    if (!rt) return 1;
    if (memory_limit > 0) JS_SetMemoryLimit(rt, (size_t) memory_limit);
    if (stack_limit > 0) JS_SetMaxStackSize(rt, (size_t) stack_limit);
    JS_SetInterruptHandler(rt, interrupt_handler, NULL);
    ctx = JS_NewContext(rt);
    if (!ctx) return 2;
    set_out_str("null");
    return 0;
}

KVR_EXPORT("kvr_memory_used") int32_t kvr_memory_used(void) {
    JSMemoryUsage u;
    JS_ComputeMemoryUsage(rt, &u);
    return (int32_t) u.malloc_size;
}

KVR_EXPORT("kvr_eval") int32_t kvr_eval(const char *src, int32_t src_len, const char *name, int32_t name_len) {
    (void) name_len;
    JSValue v = JS_Eval(ctx, src, (size_t) src_len, name, JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(v)) {
        set_out_exception();
        return 1;
    }
    JS_FreeValue(ctx, v);
    if (run_jobs() < 0) {
        set_out_exception();
        return 1;
    }
    set_out_str("null");
    return 0;
}

KVR_EXPORT("kvr_call") int32_t kvr_call(const char *fn, int32_t fn_len, const char *args, int32_t args_len) {
    (void) fn_len;
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue f = JS_GetPropertyStr(ctx, global, fn);
    JS_FreeValue(ctx, global);
    if (!JS_IsFunction(ctx, f)) {
        JS_FreeValue(ctx, f);
        set_out_str("TypeError: global function not found");
        return 1;
    }
    JSValue arr = JS_ParseJSON(ctx, args, (size_t) args_len, "<args>");
    if (JS_IsException(arr)) {
        JS_FreeValue(ctx, f);
        set_out_exception();
        return 1;
    }
    JSValue lenv = JS_GetPropertyStr(ctx, arr, "length");
    int32_t n = 0;
    JS_ToInt32(ctx, &n, lenv);
    JS_FreeValue(ctx, lenv);
    if (n < 0) n = 0;
    if (n > MAX_ARGS) n = MAX_ARGS;
    JSValue argv[MAX_ARGS];
    for (int32_t i = 0; i < n; i++) argv[i] = JS_GetPropertyUint32(ctx, arr, (uint32_t) i);
    JSValue r = JS_Call(ctx, f, JS_UNDEFINED, n, argv);
    for (int32_t i = 0; i < n; i++) JS_FreeValue(ctx, argv[i]);
    JS_FreeValue(ctx, arr);
    JS_FreeValue(ctx, f);
    if (JS_IsException(r)) {
        set_out_exception();
        return 1;
    }
    if (run_jobs() < 0) {
        JS_FreeValue(ctx, r);
        set_out_exception();
        return 1;
    }
    set_out_json(r);
    JS_FreeValue(ctx, r);
    return 0;
}

// --- host functions --------------------------------------------------------------------------

static JSValue host_trampoline(JSContext *c, JSValueConst this_val, int argc, JSValueConst *argv, int magic, JSValue *data) {
    (void) this_val;
    (void) magic;
    size_t name_len = 0;
    const char *name = JS_ToCStringLen(c, &name_len, data[0]);
    if (!name) return JS_EXCEPTION;

    JSValue arr = JS_NewArray(c);
    for (int i = 0; i < argc; i++) JS_SetPropertyUint32(c, arr, (uint32_t) i, JS_DupValue(c, argv[i]));
    JSValue json = JS_JSONStringify(c, arr, JS_UNDEFINED, JS_UNDEFINED);
    JS_FreeValue(c, arr);
    if (JS_IsException(json)) {
        JS_FreeCString(c, name);
        return JS_EXCEPTION;
    }
    size_t args_len = 0;
    const char *args = JS_ToCStringLen(c, &args_len, json);
    int32_t r = kvr_host_call(name, (int32_t) name_len, args ? args : "[]", args ? (int32_t) args_len : 2);
    if (args) JS_FreeCString(c, args);
    JS_FreeValue(c, json);
    JS_FreeCString(c, name);

    int err = r < 0;
    size_t len = err ? (size_t) (-(r + 1)) : (size_t) r;
    char *buf = (char *) malloc(len + 1);
    if (!buf) return JS_ThrowOutOfMemory(c);
    kvr_host_result(buf);
    buf[len] = 0;
    JSValue result;
    if (err) result = JS_ThrowInternalError(c, "%s", buf);
    else if (len == 0) result = JS_UNDEFINED;
    else result = JS_ParseJSON(c, buf, len, "<host>");
    free(buf);
    return result;
}

KVR_EXPORT("kvr_expose") int32_t kvr_expose(const char *name, int32_t name_len) {
    JSValue data = JS_NewStringLen(ctx, name, (size_t) name_len);
    JSValue f = JS_NewCFunctionData(ctx, host_trampoline, 0, 0, 1, &data);
    JS_FreeValue(ctx, data);
    JSValue global = JS_GetGlobalObject(ctx);
    int r = JS_SetPropertyStr(ctx, global, name, f); // takes ownership of f
    JS_FreeValue(ctx, global);
    if (r < 0) {
        set_out_exception();
        return 1;
    }
    return 0;
}
