#!/bin/bash
# Checks the jar built on JDK 9+ (mvn package): the module descriptor, a modular consumer on the module path
# with the JDK of JAVA_HOME, and, if the home of a Java 8 JDK is given, a consumer on the class path of Java 8.
# usage: .github/scripts/check-modular-jar.sh [java-8-home]
set -euo pipefail

MODULE=com.github.sftwnd.crayfish_common_crc
JAVA8_HOME=${1:-}
JAR=$(ls target/crayfish-common-crc-*.jar | grep -vE -- '-(sources|javadoc)\.jar$')
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

fail() {
    echo "FAILED: $*" >&2
    exit 1
}

# the module descriptor, in META-INF/versions/9 of a multi-release jar
DESCRIPTOR=$("$JAVA_HOME/bin/jar" --describe-module --file "$JAR" --release 9)
echo "$DESCRIPTOR"
for line in "$MODULE@" \
            "exports com.github.sftwnd.crayfish.common.crc" \
            "requires com.github.spotbugs.annotations static"; do
    grep -qF -- "$line" <<< "$DESCRIPTOR" || fail "no '$line' in the module descriptor"
done

# CRC-32/ISO-HDLC of "123456789" is cbf43926
mkdir -p "$WORK/modular/crc.check/crc/check" "$WORK/plain/crc/check" "$WORK/plain/out"
cat > "$WORK/modular/crc.check/module-info.java" <<JAVA
module crc.check {
    requires $MODULE;
}
JAVA
cat > "$WORK/modular/crc.check/crc/check/Main.java" <<'JAVA'
package crc.check;

import com.github.sftwnd.crayfish.common.crc.CrcModel;

import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        Module module = CrcModel.class.getModule();
        System.out.println(module.getName() + (module.getDescriptor().isAutomatic() ? " automatic" : " named")
                + " " + Long.toHexString(CrcModel.CRC32_ISO_HDLC.getCRC()
                        .update("123456789".getBytes(StandardCharsets.US_ASCII)).getCrc()));
    }
}
JAVA
cat > "$WORK/plain/crc/check/Main.java" <<'JAVA'
package crc.check;

import com.github.sftwnd.crayfish.common.crc.CrcModel;

import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        System.out.println(System.getProperty("java.version") + " " + Long.toHexString(CrcModel.CRC32_ISO_HDLC.getCRC()
                .update("123456789".getBytes(StandardCharsets.US_ASCII)).getCrc()));
    }
}
JAVA

# a named module on the module path, without the optional spotbugs annotations
"$JAVA_HOME/bin/javac" -d "$WORK/modular/out" --module-path "$JAR" --module-source-path "$WORK/modular" --module crc.check
OUT=$("$JAVA_HOME/bin/java" --module-path "$JAR:$WORK/modular/out/crc.check" --module crc.check/crc.check.Main)
echo "module path: $OUT"
[[ "$OUT" = "$MODULE named cbf43926" ]] || fail "expected '$MODULE named cbf43926' on the module path"

# Java 8 ignores the descriptor
if [[ -n "$JAVA8_HOME" ]]; then
    "$JAVA8_HOME/bin/javac" -d "$WORK/plain/out" -cp "$JAR" "$WORK/plain/crc/check/Main.java"
    OUT=$("$JAVA8_HOME/bin/java" -cp "$JAR:$WORK/plain/out" crc.check.Main)
    echo "class path: $OUT"
    [[ "$OUT" == 1.8.* && "$OUT" == *" cbf43926" ]] || fail "expected 'cbf43926' on the class path of Java 8"
fi
echo "OK"
