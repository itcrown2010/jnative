/* JNative crypto runtime (0.25) — CSPRNG + integer bitwise helpers for SHA/HMAC. */
#include <stdint.h>
#include <string.h>

#if defined(_WIN32)
#include <windows.h>
#include <bcrypt.h>
#pragma comment(lib, "bcrypt.lib")
#elif defined(__APPLE__)
#include <Security/SecRandom.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#else
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#if defined(__linux__)
#include <sys/random.h>
#endif
#endif

int jnative_secure_random(uint8_t* out, int32_t len) {
    if (out == NULL || len <= 0) {
        return -1;
    }
#if defined(_WIN32)
    NTSTATUS st = BCryptGenRandom(NULL, out, (ULONG)len, BCRYPT_USE_SYSTEM_PREFERRED_RNG);
    return st == 0 ? 0 : -2;
#elif defined(__APPLE__)
    if (SecRandomCopyBytes(kSecRandomDefault, (size_t)len, out) == errSecSuccess) {
        return 0;
    }
    /* fall through to /dev/urandom */
#elif defined(__linux__)
    size_t got = 0;
    while (got < (size_t)len) {
        ssize_t n = getrandom(out + got, (size_t)len - got, 0);
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        got += (size_t)n;
    }
    if (got == (size_t)len) return 0;
    /* fallback /dev/urandom */
#endif
#if !defined(_WIN32)
    {
        int fd = open("/dev/urandom", O_RDONLY);
        if (fd < 0) return -3;
        size_t got = 0;
        while (got < (size_t)len) {
            ssize_t n = read(fd, out + got, (size_t)len - got);
            if (n <= 0) {
                close(fd);
                return -4;
            }
            got += (size_t)n;
        }
        close(fd);
        return 0;
    }
#else
    return -5;
#endif
}

int32_t jnative_bit_and(int32_t a, int32_t b) { return a & b; }
int32_t jnative_bit_or(int32_t a, int32_t b) { return a | b; }
int32_t jnative_bit_xor(int32_t a, int32_t b) { return a ^ b; }
int32_t jnative_bit_not(int32_t a) { return ~a; }
int32_t jnative_bit_shl(int32_t a, int32_t n) { return a << (n & 31); }
int32_t jnative_bit_ushr(int32_t a, int32_t n) { return (int32_t)(((uint32_t)a) >> (n & 31)); }
