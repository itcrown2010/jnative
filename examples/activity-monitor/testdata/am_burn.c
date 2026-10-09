/* Author: 张晓达 — CPU burn test process */
#include <signal.h>
#include <stdio.h>
#include <unistd.h>

static volatile int keep = 1;
static void on_term(int sig) {
    (void)sig;
    keep = 0;
}

int main(void) {
    signal(SIGTERM, on_term);
    printf("am_burn pid=%d\n", (int)getpid());
    fflush(stdout);
    volatile unsigned long x = 1;
    while (keep) {
        x = x * 1103515245u + 12345u;
    }
    return (int)(x & 1);
}
