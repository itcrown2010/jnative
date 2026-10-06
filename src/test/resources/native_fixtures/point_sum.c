#include <stddef.h>

typedef struct {
    double x;
    double y;
} Point;

typedef struct {
    char a;
    long long b;
    short c;
} Mixed;

typedef struct {
    Point a;
    Point b;
} Line;

double point_sum(Point* p) {
    return p->x + p->y;
}

/* Layout probe helpers for ABI cross-check tests (compiled into same lib). */
long long jn_sizeof_Point(void) { return (long long)sizeof(Point); }
long long jn_alignof_Point(void) { return (long long)_Alignof(Point); }
long long jn_offsetof_Point_x(void) { return (long long)offsetof(Point, x); }
long long jn_offsetof_Point_y(void) { return (long long)offsetof(Point, y); }

long long jn_sizeof_Mixed(void) { return (long long)sizeof(Mixed); }
long long jn_alignof_Mixed(void) { return (long long)_Alignof(Mixed); }
long long jn_offsetof_Mixed_a(void) { return (long long)offsetof(Mixed, a); }
long long jn_offsetof_Mixed_b(void) { return (long long)offsetof(Mixed, b); }
long long jn_offsetof_Mixed_c(void) { return (long long)offsetof(Mixed, c); }

long long jn_sizeof_Line(void) { return (long long)sizeof(Line); }
long long jn_offsetof_Line_a(void) { return (long long)offsetof(Line, a); }
long long jn_offsetof_Line_b(void) { return (long long)offsetof(Line, b); }
