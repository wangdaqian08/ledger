#!/usr/bin/env bash
# Updates Ledger on the Home PC to a GitHub Release. Run in Git Bash:
#   ./update.sh            the latest release
#   ./update.sh v0.2.5     a specific one (also how to roll back to an older tag)
#   ./update.sh --rollback the jar that was running before the last update
# `cat VERSION` says which release is deployed; releases/ledger-prev.version says what a rollback
# would bring back.
set -euo pipefail
cd /c/ledger-home
mkdir -p backups releases

repo=https://github.com/wangdaqian08/ledger

if [ "${1:-}" = "--rollback" ]; then
cp releases/ledger-prev.jar ledger.jar.new
tag=$(cat releases/ledger-prev.version 2>/dev/null || echo unknown)
else
tag=${1:-latest}
# "latest" is resolved to its real tag first, so VERSION records what was installed rather than
# the word "latest". GitHub redirects /releases/latest to /releases/tag/<tag>.
if [ "$tag" = latest ]; then
 tag=$(curl -fsSL -o /dev/null -w '%{url_effective}' "$repo/releases/latest")
 tag=${tag##*/}
fi
case "$tag" in
 v*) ;;
 *) echo "could not work out a release tag (got '$tag'), nothing changed"; exit 1 ;;
esac
curl -fL -o ledger.jar.new "$repo/releases/download/$tag/ledger.jar"
fi

# A real boot jar is tens of MB. Anything tiny is an error page, so stop before touching anything.
[ "$(wc -c < ledger.jar.new)" -gt 10000000 ] || { echo "download looks wrong, nothing changed"; exit 1; }

docker compose exec -T ledger-db sh -c 'pg_dump -U $POSTGRES_USER -Fc $POSTGRES_DB' > "backups/ledger-$(date +%F-%H%M).dump"
cp ledger.jar releases/ledger-prev.jar
{ cat VERSION 2>/dev/null || echo unknown; } > releases/ledger-prev.version
mv ledger.jar.new ledger.jar
echo "$tag" > VERSION
docker compose up -d --force-recreate ledger

# 401 means the app is up and refusing an anonymous caller, the same check as the VM runbook.
for _ in $(seq 60); do
code=$(curl -s -o /dev/null -w '%{http_code}' https://private.youplay123.online/ledger/api/me || true)
[ "$code" = 401 ] && { echo "Ledger $tag is up"; exit 0; }
sleep 2
done
echo "Ledger $tag did not come up in 2 minutes: docker compose logs ledger"; exit 1

