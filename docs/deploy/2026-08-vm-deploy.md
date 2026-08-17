# Deploying Ledger to https://youplay123.online/ledger

The zero-budget interim deployment (spec §4 records the deviations from the Cloud Run target):
the boot jar with the SPA embedded, under systemd, behind the nginx that already serves the
Werewolf app at the domain root, with Postgres 18 on the same free-tier VM.

## Shape

```
browser ── https ──> nginx (TLS, VM werewolf-server, e2-micro us-east1-d)
                       ├── /            → Werewolf (untouched: backend :8080, frontend :8081,
                       │                  all Docker Compose under /opt/werewolf-simple)
                       └── /ledger/     → 127.0.0.1:8082  Spring Boot, context-path /ledger,
                                          SPA embedded in the jar, Postgres on loopback :5432
```

- One artifact: `ledger.jar` (bootJar with `web/dist` at `BOOT-INF/classes/static`).
- All environment lives in `/etc/ledger/env` (root-only, 0600): profiles `prod,name-signin`,
  port/address/context-path, datasource, Hikari pool, invite secret.
- The unit is `ledger.service`; `OOMScoreAdjust=500` makes Ledger the OOM victim before Werewolf.
- **The database is a second database inside Werewolf's existing `postgres:16-alpine` container**
  (role/db `ledger`), not a new instance: the box was already 650 MB into swap, and a second
  Postgres would spend memory to buy nothing but version purity. The 16-vs-18 skew against the
  test-pinned image is a recorded trade — the schema uses nothing newer than either. The cost is
  a shared lifecycle: `docker compose down` in /opt/werewolf-simple takes Ledger's database too.
- Nightly `pg_dump` cron in `/etc/cron.daily/ledger-backup` (runs inside the container), 7-day
  retention, same disk (accepted single point of failure).

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
  --command='curl -s -o /dev/null -w %{http_code}\\n http://127.0.0.1:8082/ledger/api/me'
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

## Facts from the first deployment (2026-08-17, main @ 4a000ac)

- Ports on the VM: werewolf backend 8080, werewolf frontend container 8081, **ledger 8082**,
  postgres 5432 — all loopback-published; nothing new faces the network.
- Java: Temurin **25.0.4** at `/usr/lib/jvm/temurin-25-jre-amd64/bin/java` (host `java` is
  a pre-existing 17 — the unit uses the full path).
- Database: `ledger` role + database inside `werewolf-simple-postgres-1` (PostgreSQL **16.14**).
  Flyway applied V1→V3 on first boot in 0.45 s.
- nginx site file: `/etc/nginx/sites-enabled/werewolf` → `/etc/nginx/sites-available/werewolf`
  (backup `werewolf.pre-ledger.2026-08-17` beside it). The two `/ledger` blocks sit above the
  SPA catch-all. `certbot renew --dry-run` passes with them in place.
- Memory: 2 G swapfile pre-existed. After start + a browser session: ~160–230 MB available,
  swap steady, no thrashing (`vmstat` si/so ≈ 0). Ledger JVM ~24 s to start on the shared vCPU.
- Werewolf regression: root page byte-identical to the pre-deploy baseline; `/api/health` 200;
  `/api/` 403 — unchanged.
- Verified live: sign-in (name provider, `@name.invalid` emails), trip + members + invite link
  `https://youplay123.online/ledger/join/<id>#token=…`, the already-aboard card, sign-out
  round-trip with the fragment intact, seat claim, CSV export at `/ledger/api/...` (200,
  `text/csv`). `Set-Cookie: LEDGER-XSRF=…; Path=/ledger; Secure` proves the forward-headers
  chain.
- Quirk, accepted: `HEAD /ledger/` answers 401 (the shell's permitAll is GET-only). Browsers
  send GET; if an uptime checker ever wants HEAD, widen the matcher deliberately.

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
