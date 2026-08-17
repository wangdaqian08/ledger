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

## Receipts (feature added 2026-08-18; bucket created at its first deploy)

Receipt images live in a Cloud Storage bucket — `ledger-receipts-werewolf-301709`, **us-east1**
regional standard storage, uniform access, public access prevention on. That is inside the
always-free envelope (5 GB-months, 5,000 Class A + 50,000 Class B ops/month, shared across
us-east1/west1/central1), so storage and operations bill $0 at friends scale; bytes served to
browsers ride the same cents-scale egress path as everything else this host answers. The app
reads GCS→VM in-region, which is free.

Three additions to `/etc/ledger/env`:

    SPRING_PROFILES_ACTIVE=prod,name-signin,gcs-receipts
    LEDGER_RECEIPTS_GCS_BUCKET=ledger-receipts-werewolf-301709
    GOOGLE_APPLICATION_CREDENTIALS=/etc/ledger/gcs-key.json

The VM's default service account carries the read-only storage *scope*, and changing scopes means
stopping the instance (werewolf downtime). So writes use a dedicated service account
(`ledger-receipts@werewolf-301709.iam.gserviceaccount.com`) granted `objectAdmin` on this one
bucket and nothing else; its key file sits beside the env file, root-owned, 0600.

Retention is the app's own daily sweep (`ReceiptRetention`, 14 days after a trip's `closed_at`),
**not** a bucket lifecycle rule — an age-based rule would delete the receipts of any trip that
simply runs long. A crashed upload can in principle strand an unreferenced object; at this scale
that is a hand-cleanable curiosity, visible with `gcloud storage ls`.
