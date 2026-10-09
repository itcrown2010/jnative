/* Author: 张晓达 — idle test process for activity-monitor */
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
    printf("am_idle pid=%d\n", (int)getpid());
    fflush(stdout);
    while (keep) {
        sleep(1);
    }
    return 0;
}
