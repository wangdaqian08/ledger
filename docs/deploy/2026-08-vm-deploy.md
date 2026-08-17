# Deploying Ledger to https://youplay123.online/ledger

The zero-budget interim deployment (spec §4 records the deviations from the Cloud Run target):
the boot jar with the SPA embedded, under systemd, behind the nginx that already serves the
Werewolf app at the domain root, with Postgres 18 on the same free-tier VM.

## Shape

```
browser ── https ──> nginx (TLS, VM werewolf-server, e2-micro us-east1-d)
                       ├── /            → Werewolf (untouched)
                       └── /ledger/     → 127.0.0.1:8081  Spring Boot, context-path /ledger,
                                          SPA embedded in the jar, Postgres 18 on loopback
```

- One artifact: `ledger.jar` (bootJar with `web/dist` at `BOOT-INF/classes/static`).
- All environment lives in `/etc/ledger/env` (root-only, 0600): profiles `prod,name-signin`,
  port/address/context-path, datasource, Hikari pool, invite secret.
- The unit is `ledger.service`; `OOMScoreAdjust=500` makes Ledger the OOM victim before Werewolf.
- Postgres role/db `ledger`, tuned small (`conf.d/ledger-tiny.conf`); nightly `pg_dump` cron in
  `/etc/cron.daily/ledger-backup`, 7-day retention, same disk (accepted single point of failure).

## Releasing a new version

```bash
git checkout main && git pull                       # deploys always come from merged main
npm --prefix web ci
VITE_BASE=/ledger/ npm --prefix web run build       # base is the one fact that moves the SPA
./gradlew :server:bootJar -PrequireSpa=/ledger/     # refuses a missing or wrong-base bundle
gcloud compute scp server/build/libs/ledger.jar werewolf-server:/tmp/ledger-<sha>.jar --zone=us-east1-d
gcloud compute ssh werewolf-server --zone=us-east1-d --command='
  sudo install -m 0644 /tmp/ledger-<sha>.jar /opt/ledger/releases/ledger-<sha>.jar &&
  sudo ln -sfn /opt/ledger/releases/ledger-<sha>.jar /opt/ledger/ledger.jar &&
  sudo systemctl restart ledger'
# health: poll for 401 on loopback, then check the public URL
gcloud compute ssh werewolf-server --zone=us-east1-d \
  --command='curl -s -o /dev/null -w %{http_code}\\n http://127.0.0.1:8081/ledger/api/me'
```

Sessions and data survive restarts (both live in Postgres). Open tabs may need one refresh —
index.html is `no-cache`, assets are content-hashed.

## Rollback

- Bad release: `sudo ln -sfn /opt/ledger/releases/ledger-<previous>.jar /opt/ledger/ledger.jar &&
  sudo systemctl restart ledger`. Migrations must therefore stay backward-compatible for one
  release.
- App down, keep Werewolf: `sudo systemctl stop ledger` — nginx keeps serving Werewolf; `/ledger`
  returns 502 until restarted.
- Full retreat: stop+disable the unit, remove the two `/ledger` location blocks from the nginx
  site file, `nginx -t && systemctl reload nginx` — the host is exactly as before Ledger.

## Facts from the first deployment

<!-- filled in by the close-out after Phase F: werewolf's port, chosen PG port, measured RAM
     headroom under load, nginx site file path, first deployed sha -->
