#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>
#include <stdint.h>

static int listen_port(int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons((uint16_t)port);
    bind(fd, (struct sockaddr *)&addr, sizeof(addr));
    listen(fd, 16);
    return fd;
}

static void serve_once(int lfd, const char *resp) {
    int c = accept(lfd, NULL, NULL);
    char buf[4096];
    recv(c, buf, sizeof(buf) - 1, 0);
    send(c, resp, strlen(resp), 0);
    close(c);
}

int main(int argc, char **argv) {
    int portA = atoi(argv[1]);
    int portB = atoi(argv[2]);
    int a = listen_port(portA);
    int b = listen_port(portB);
    printf("ready\n");
    fflush(stdout);
    char loc[256];
    snprintf(loc, sizeof(loc),
        "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:%d/target\r\nConnection: close\r\nContent-Length: 0\r\n\r\n",
        portB);
    serve_once(a, loc);
    int c = accept(b, NULL, NULL);
    char buf[4096];
    memset(buf, 0, sizeof(buf));
    recv(c, buf, sizeof(buf) - 1, 0);
    int hasAuth = strstr(buf, "Authorization:") != NULL;
    int hasKeep = strstr(buf, "X-Keep: yes") != NULL;
    char body[64];
    if (hasAuth) {
        snprintf(body, sizeof(body), "has-auth");
    } else if (hasKeep) {
        snprintf(body, sizeof(body), "no-auth keep=yes");
    } else {
        snprintf(body, sizeof(body), "no-auth");
    }
    char resp[256];
    snprintf(resp, sizeof(resp),
        "HTTP/1.1 200 OK\r\nContent-Length: %zu\r\nConnection: close\r\n\r\n%s",
        strlen(body), body);
    send(c, resp, strlen(resp), 0);
    close(c);
    close(a);
    close(b);
    return 0;
}
