/*
 * Minimal HTTP/1.1 fixture for JNative 0.8.3 E2E.
 * argv[1]=port argv[2]=mode: hello|chunked|binary|post|redirect|large
 */
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

static void send_all(int fd, const char *data, size_t n) {
    while (n > 0) {
        ssize_t w = send(fd, data, n, 0);
        if (w <= 0) return;
        data += (size_t)w;
        n -= (size_t)w;
    }
}

static void send_str(int fd, const char *s) {
    send_all(fd, s, strlen(s));
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <port> <mode>\n", argv[0]);
        return 2;
    }
    int port = atoi(argv[1]);
    const char *mode = argv[2];

    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) { perror("socket"); return 1; }
    int one = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons((uint16_t)port);
    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) { perror("bind"); return 1; }
    if (listen(fd, 8) < 0) { perror("listen"); return 1; }
    printf("READY\n");
    fflush(stdout);

    int client = accept(fd, NULL, NULL);
    if (client < 0) { perror("accept"); return 1; }

    char req[8192];
    ssize_t n = recv(client, req, sizeof(req) - 1, 0);
    if (n < 0) n = 0;
    req[n] = 0;

    if (strcmp(mode, "hello") == 0) {
        send_str(client,
            "HTTP/1.1 200 OK\r\n"
            "Content-Length: 13\r\n"
            "Connection: close\r\n"
            "\r\n"
            "Hello JNative");
    } else if (strcmp(mode, "chunked") == 0) {
        send_str(client,
            "HTTP/1.1 200 OK\r\n"
            "Transfer-Encoding: chunked\r\n"
            "Connection: close\r\n"
            "\r\n"
            "6\r\nHello \r\n"
            "7\r\nJNative\r\n"
            "0\r\n\r\n");
    } else if (strcmp(mode, "binary") == 0) {
        send_str(client,
            "HTTP/1.1 200 OK\r\n"
            "Content-Type: application/octet-stream\r\n"
            "Content-Length: 5\r\n"
            "Connection: close\r\n"
            "\r\n");
        unsigned char bin[] = {0x00, 0xFF, 0x01, 0x00, 0x7F};
        send_all(client, (const char *)bin, 5);
    } else if (strcmp(mode, "post") == 0) {
        /* echo body after headers */
        char *body = strstr(req, "\r\n\r\n");
        const char *payload = body ? body + 4 : "";
        char hdr[256];
        int blen = (int)strlen(payload);
        snprintf(hdr, sizeof(hdr),
            "HTTP/1.1 200 OK\r\nContent-Length: %d\r\nConnection: close\r\n\r\n", blen);
        send_str(client, hdr);
        send_all(client, payload, (size_t)blen);
    } else if (strcmp(mode, "redirect") == 0) {
        char loc[128];
        snprintf(loc, sizeof(loc),
            "HTTP/1.1 302 Found\r\n"
            "Location: http://127.0.0.1:%d/hello\r\n"
            "Content-Length: 0\r\n"
            "Connection: close\r\n\r\n", port);
        send_str(client, loc);
        close(client);
        client = accept(fd, NULL, NULL);
        if (client < 0) { perror("accept2"); return 1; }
        n = recv(client, req, sizeof(req) - 1, 0);
        (void)n;
        send_str(client,
            "HTTP/1.1 200 OK\r\n"
            "Content-Length: 13\r\n"
            "Connection: close\r\n"
            "\r\n"
            "Hello JNative");
    } else if (strcmp(mode, "large") == 0) {
        int len = 1048576;
        char hdr[128];
        snprintf(hdr, sizeof(hdr),
            "HTTP/1.1 200 OK\r\nContent-Length: %d\r\nConnection: close\r\n\r\n", len);
        send_str(client, hdr);
        char block[4096];
        memset(block, 'A', sizeof(block));
        int left = len;
        while (left > 0) {
            int m = left > (int)sizeof(block) ? (int)sizeof(block) : left;
            send_all(client, block, (size_t)m);
            left -= m;
        }
    } else if (strcmp(mode, "file") == 0) {
        unsigned char bin[] = {0x00, 0xFF, 0x01, 0x00, 0x7F};
        send_str(client,
            "HTTP/1.1 200 OK\r\n"
            "Content-Length: 5\r\n"
            "Connection: close\r\n"
            "\r\n");
        send_all(client, (const char *)bin, 5);
    } else {
        fprintf(stderr, "unknown mode %s\n", mode);
        return 2;
    }

    close(client);
    close(fd);
    return 0;
}
