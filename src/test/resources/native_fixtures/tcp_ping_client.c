/* Author: 张晓达 */
/*
 * Native TCP client fixture: connect, send "ping", print response, exit.
 * argv[1]=host argv[2]=port
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
        fprintf(stderr, "usage: %s <host> <port>\n", argv[0]);
        return 2;
    }
    const char *host = argv[1];
    int port = atoi(argv[2]);

    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        perror("socket");
        return 1;
    }
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((uint16_t)port);
    if (inet_pton(AF_INET, host, &addr.sin_addr) != 1) {
        fprintf(stderr, "bad host\n");
        return 1;
    }
    if (connect(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("connect");
        return 1;
    }
    const char *ping = "ping";
    if (send(fd, ping, 4, 0) != 4) {
        perror("send");
        return 1;
    }
    char buf[256];
    ssize_t n = recv(fd, buf, sizeof(buf) - 1, 0);
    if (n < 0) {
        perror("recv");
        return 1;
    }
    buf[n] = 0;
    printf("%s\n", buf);
    close(fd);
    return 0;
}
