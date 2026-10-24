#!/bin/sh
# JNative Studio launcher.
#   ./studio.sh                 start Studio (builds it first when needed)
#   ./studio.sh --open DIR      start and open the JNative project in DIR
#   ./studio.sh --rebuild       force a rebuild of Studio itself
#
# Studio builds user projects with the JNative compiler. It is located through, in order:
#   $JNATIVE_COMPILER (a jnative.jar or a target/classes directory), $JNATIVE_HOME/target/..., or the
#   checkout this script lives in.
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"

if [ -z "$JNATIVE_COMPILER" ]; then
    if [ -f "$ROOT/target/classes/io/jnative/Main.class" ]; then
        JNATIVE_COMPILER="$ROOT/target/classes"
    elif [ -f "$ROOT/target/jnative.jar" ]; then
        JNATIVE_COMPILER="$ROOT/target/jnative.jar"
    fi
fi
export JNATIVE_COMPILER

BIN="$HERE/.jnative/build/jnative-studio"
REBUILD=0
if [ "$1" = "--rebuild" ]; then
    REBUILD=1
    shift
fi
if [ ! -x "$BIN" ] || [ "$REBUILD" = "1" ]; then
    if [ -z "$JNATIVE_COMPILER" ]; then
        echo "JNative compiler not found. Build it first (mvn -o -q process-resources compile) or set JNATIVE_COMPILER." >&2
        exit 1
    fi
    echo "Building JNative Studio ..."
    case "$JNATIVE_COMPILER" in
        *.jar) (cd "$HERE" && java -jar "$JNATIVE_COMPILER" build --project .) ;;
        *) (cd "$HERE" && java -cp "$JNATIVE_COMPILER" io.jnative.Main build --project .) ;;
    esac
fi
exec "$BIN" "$@"
