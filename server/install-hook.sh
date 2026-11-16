#!/system/bin/sh
set -eu
start=/data/local/server/start.sh
if [ -f "$start" ] && ! grep -q 'start-kela.sh' "$start"; then
 printf '\n[ ! -x /data/local/server/start-kela.sh ] || /data/local/server/start-kela.sh\n' >> "$start"
fi