# Releasing Ledger to the Home PC

Since 2026-10-03 Ledger runs on the Home PC at `https://private.youplay123.online/ledger`. The VM's
nginx 302s the old `youplay123.online/ledger` there. Ledger is down whenever the PC is off.

## What's on the PC (`C:\ledger-home`, Git Bash)

| File | Role |
 |---|---|
| `compose.yaml` | project `ledger-home`: `ledger-db` (postgres:18-alpine, volume `ledger-pgdata`) + `ledger` (temurin:25-jre running `./ledger.jar` on :8082, context path `/ledger`), joined to `werewolf-home_default` |
| `ledger.env` | profiles, `LEDGER_INVITE_SECRET` (never change it, or open invite links die), bucket, DB creds |
| `ledger.jar` | the release, mounted read-only |
| `gcs-key.json` | receipts bucket key |

The Cloudflare tunnel (in the Werewolf stack) routes `^/ledger` → `http://ledger:8082`, so Werewolf
must be up for the network to exist.

## Release a new version

**Mac** (from merged `main`, whose CI is green):
 ```bash
 git checkout main && git pull
 git tag v0.X.Y && git push origin v0.X.Y
 ```
The tag triggers `.github/workflows/release.yml`, which builds the `/ledger/` jar and attaches it
to a GitHub Release (about 3 minutes, Actions tab → "Release").

**PC**, Git Bash:
 ```bash
 cd /c/ledger-home && ./update.sh          # latest release
 ```
`update.sh` (copy of `deploy/home-pc/update.sh`) downloads the jar, refuses anything under 10 MB,
backs up the database, keeps the old jar as `releases/ledger-prev.jar`, restarts `ledger`, and
waits for the public URL to answer 401. "Latest" is resolved to its real tag first, which is
written to `VERSION` (the previous one to `releases/ledger-prev.version`):
 ```bash
 cat /c/ledger-home/VERSION    # what is deployed — the sign-in screen shows the same tag
 ```

First time only, and again whenever the script changes, fetch it onto the PC:
 ```bash
 cd /c/ledger-home
 curl -fLO https://raw.githubusercontent.com/wangdaqian08/ledger/main/deploy/home-pc/update.sh && chmod +x update.sh
 ```

## Rollback

 ```bash
 ./update.sh --rollback      # the jar that ran before the last update
 ./update.sh v0.2.4          # or any earlier tag
 ```
If the bad release ran a new Flyway migration that the old jar can't validate, also restore the
dump `update.sh` took (`backups/`):
 ```bash
 docker compose stop ledger
 docker compose exec -T ledger-db sh -c 'pg_restore -U $POSTGRES_USER -d $POSTGRES_DB --clean --if-exists' < backups/ledger-YYYY-MM-DD-HHMM.dump
 docker compose up -d ledger
 ```

## Day to day

- Start: start Werewolf first (for the tunnel and network), then `cd /c/ledger-home && docker compose up -d --wait`.
- Stop: `docker compose stop`. Never run `down -v`, which deletes the database volume.
- Backup after each session (`update.sh` also takes one before every release), and keep a copy off the PC.
