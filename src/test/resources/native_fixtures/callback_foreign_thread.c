#include <pthread.h>

typedef int (*callback_t)(int);

struct args {
    callback_t cb;
    int value;
    int result;
};

static void *worker(void *p) {
    struct args *a = p;
    a->result = a->cb(a->value);
    return NULL;
}

int invoke_callback_on_thread(callback_t callback, int value) {
    struct args a = { callback, value, 0 };
    pthread_t t;
    pthread_create(&t, NULL, worker, &a);
    pthread_join(t, NULL);
    return a.result;
}
