#!/bin/sh
set -eu
cd "$(dirname "$0")/bin"
mkdir -p logs
hearthborn_java_home=$(/usr/libexec/java_home -v 22)
# Apple Silicon: size the JVM to the performance cores (efficiency cores make parallel ticks and GC wait for stragglers)
# and keep large arrays out of humongous G1 allocations.
hearthborn_tuning=""
if [ "$(uname -m)" = "arm64" ]; then
    hearthborn_perf=$(sysctl -n hw.perflevel0.physicalcpu 2>/dev/null || echo 0)
    if [ "$hearthborn_perf" -ge 2 ] 2>/dev/null; then
        hearthborn_tuning="-XX:ActiveProcessorCount=$hearthborn_perf"
    fi
    hearthborn_tuning="$hearthborn_tuning -Xms1g -XX:G1HeapRegionSize=4m"
fi
# shellcheck disable=SC2086
exec "$hearthborn_java_home/bin/java" \
    -Xms512m -Xmx2048m $hearthborn_tuning \
    -XX:ErrorFile=logs/jvm-crash-%p.log \
    --enable-native-access=ALL-UNNAMED \
    -Dsun.java2d.uiScale.enabled=false \
    -Dhaven.toolkit=cgl \
    -jar hafen.jar "$@"
