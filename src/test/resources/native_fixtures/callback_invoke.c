typedef int (*callback_t)(int);

int invoke_callback(callback_t callback, int value) {
    return callback(value);
}
