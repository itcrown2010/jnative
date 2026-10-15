#!/usr/bin/env bash
# Author: 张晓达
# Diagnostic checks for package/import resolution (must fail when import missing).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$ROOT/../.." && pwd)"
JAR="${JNATIVE_JAR:-$REPO/target/jnative.jar}"
WORK="$ROOT/build/pkg-verify"
OUT="$ROOT/build"
rm -rf "$WORK"
mkdir -p "$WORK"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR" >&2
  exit 1
fi
if [[ ! -f "$OUT/libjn_zip.a" ]]; then
  "$ROOT/scripts/build.sh"
fi

ZIP_SRCS=()
while IFS= read -r _src; do
  ZIP_SRCS+=("$_src")
done < <(find "$ROOT/src/java/util/zip" "$ROOT/src/org/jnative/archive/internal" -name '*.java' | sort)
JN_LINK=(-L "$OUT" -l jn_zip -l z -l iconv)
OS="$(uname -s 2>/dev/null || echo unknown)"
if [[ "$OS" == Linux ]]; then
  JN_LINK+=(-l pthread)
fi

pass=0
fail=0

expect_fail() {
  local name="$1"
  local src="$2"
  shift 2
  echo "==> expect FAIL: $name"
  if java -jar "$JAR" "$@" "$src" -o "$WORK/$name" -O0 "${JN_LINK[@]}" >"$WORK/$name.log" 2>&1; then
    echo "UNEXPECTED PASS: $name" >&2
    cat "$WORK/$name.log" >&2
    fail=$((fail + 1))
  else
    if grep -Eiq 'undefined class|undefined type|undefined variable|cannot resolve|not found|is not accessible|unknown type|no suitable|cannot access|use a fully qualified name or import' "$WORK/$name.log"; then
      echo "OK diagnostic: $name"
      pass=$((pass + 1))
    else
      echo "FAIL wrong diagnostic for $name:" >&2
      cat "$WORK/$name.log" >&2
      fail=$((fail + 1))
    fi
  fi
}

expect_ok() {
  local name="$1"
  local src="$2"
  shift 2
  echo "==> expect OK: $name"
  if java -jar "$JAR" "$@" "$src" -o "$WORK/$name" -O0 "${JN_LINK[@]}" >"$WORK/$name.log" 2>&1; then
    echo "OK compile: $name"
    pass=$((pass + 1))
  else
    echo "UNEXPECTED FAIL: $name" >&2
    cat "$WORK/$name.log" >&2
    fail=$((fail + 1))
  fi
}

# 1) explicit import OK
cat >"$WORK/OkImport.java" <<'EOF'
package pkg.verify;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
public class OkImport {
  public static void main(String[] args) {
    ZipOutputStream z = new ZipOutputStream("examples/jnative-archive/build/pkg-verify/ok.zip");
    z.putNextEntry(new ZipEntry("a.txt"));
    z.closeEntry();
    z.finish();
    z.close();
    System.out.println("OK");
  }
}
EOF
expect_ok ok-import "$WORK/OkImport.java" "${ZIP_SRCS[@]}"

# 2) missing import — ZipEntry simple name unresolved
cat >"$WORK/MissingImport.java" <<'EOF'
package pkg.verify;
public class MissingImport {
  public static void main(String[] args) {
    ZipEntry e = new ZipEntry("x");
    System.out.println(e.getName());
  }
}
EOF
expect_fail missing-import "$WORK/MissingImport.java" "${ZIP_SRCS[@]}"

# 3) unknown type (as method argument — avoids parser null quirks)
cat >"$WORK/UnknownType.java" <<'EOF'
package pkg.verify;
public class UnknownType {
  public static void use(NoSuchZipThing x) {
  }
  public static void main(String[] args) {
    System.out.println("x");
  }
}
EOF
expect_fail unknown-type "$WORK/UnknownType.java"

# 4) same simple name across packages — need explicit import / FQN
mkdir -p "$WORK/a" "$WORK/b"
cat >"$WORK/a/Node.java" <<'EOF'
package pkg.verify.a;
public class Node {
  public static int v() { return 1; }
}
EOF
cat >"$WORK/b/Node.java" <<'EOF'
package pkg.verify.b;
public class Node {
  public static int v() { return 2; }
}
EOF
cat >"$WORK/SameName.java" <<'EOF'
package pkg.verify;
import pkg.verify.a.Node;
public class SameName {
  public static void main(String[] args) {
    System.out.println(Node.v());
    System.out.println(pkg.verify.b.Node.v());
  }
}
EOF
expect_ok same-name "$WORK/SameName.java" "$WORK/a/Node.java" "$WORK/b/Node.java"

# 5) cross-package without import fails
cat >"$WORK/CrossNoImport.java" <<'EOF'
package pkg.verify;
public class CrossNoImport {
  public static void main(String[] args) {
    System.out.println(Node.v());
  }
}
EOF
expect_fail cross-no-import "$WORK/CrossNoImport.java" "$WORK/a/Node.java"

echo "PASS=$pass FAIL=$fail"
if [[ "$fail" -ne 0 ]]; then
  exit 1
fi
echo "PACKAGE_VERIFY_OK"
