/*
 * Local TCP echo / response fixture for JNative 0.8.2 E2E tests.
 * Protocol:
 *   argv[1] = port
 *   argv[2] = mode: "echo" | "hello" | "pong"
 *   Writes "READY\n" to stdout when listening, then serves one connection.
 */
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <port> <echo|hello|pong>\n", argv[0]);
        return 2;
    }
    int port = atoi(argv[1]);
    const char *mode = argv[2];

    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        perror("socket");
        return 1;
    }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons((uint16_t)port);

    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("bind");
        return 1;
    }
    if (listen(fd, 1) < 0) {
        perror("listen");
        return 1;
    }
    printf("READY\n");
    fflush(stdout);

    int client = accept(fd, NULL, NULL);
    if (client < 0) {
        perror("accept");
        return 1;
    }

    unsigned char buf[65536];
    ssize_t n = recv(client, buf, sizeof(buf), 0);
    if (n < 0) {
        perror("recv");
        return 1;
    }

    if (strcmp(mode, "echo") == 0) {
        if (n > 0) {
            send(client, buf, (size_t)n, 0);
        }
    } else if (strcmp(mode, "hello") == 0) {
        const char *msg = "Hello Native Client";
        send(client, msg, strlen(msg), 0);
    } else if (strcmp(mode, "pong") == 0) {
        const char *msg = "pong";
        send(client, msg, strlen(msg), 0);
    } else {
        fprintf(stderr, "unknown mode %s\n", mode);
        return 2;
    }

    close(client);
    close(fd);
    return 0;
}
