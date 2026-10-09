/* Author: 张晓达 — ignores SIGTERM; only SIGKILL ends it */
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

int main(void) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = SIG_IGN;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0;
    if (sigaction(SIGTERM, &sa, NULL) != 0) {
        perror("sigaction");
        return 1;
    }
    printf("am_ignore_term pid=%d\n", (int)getpid());
    fflush(stdout);
    for (;;) {
        pause();
    }
    return 0;
}
