/* Author: 张晓达 */
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

int invoke_callback_four_threads(callback_t callback) {
    struct args threads[4];
    pthread_t ids[4];
    int i = 0;
    while (i < 4) {
        threads[i].cb = callback;
        threads[i].value = 1;
        threads[i].result = 0;
        pthread_create(&ids[i], NULL, worker, &threads[i]);
        i = i + 1;
    }
    i = 0;
    int sum = 0;
    while (i < 4) {
        pthread_join(ids[i], NULL);
        sum = sum + threads[i].result;
        i = i + 1;
    }
    return sum;
}
