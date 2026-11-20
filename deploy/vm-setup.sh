#!/usr/bin/env bash
# Startup script of the GCP VM (runs as root on every boot; safe to run again).
# Installs Docker, writes the production compose file, Caddy config and daily backup,
# and creates /opt/mywordstory/.env with a random DB password and API token.
set -euo pipefail

meta() { curl -fs -H "Metadata-Flavor: Google" "http://metadata.google.internal/computeMetadata/v1/instance/attributes/$1" || true; }
DOMAIN="$(meta domain)"
BUCKET="$(meta backup-bucket)"
DIR=/opt/mywordstory
mkdir -p "$DIR"

# 2 GB swap: the VM has 1 GB of RAM for PostgreSQL, the JVM and Caddy.
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
swapon -a || true

command -v docker >/dev/null || curl -fsSL https://get.docker.com | sh

if [ ! -f "$DIR/.env" ]; then
  umask 077
  cat > "$DIR/.env" <<EOF
DATABASE_PASSWORD=$(openssl rand -hex 24)
API_TOKEN=$(openssl rand -hex 32)
ANTHROPIC_API_KEY=
EOF
fi

cat > "$DIR/compose.yml" <<'EOF'
services:
  db:
    image: postgres:17
    restart: unless-stopped
    command: postgres -c shared_buffers=64MB -c max_connections=20
    environment:
      POSTGRES_DB: mywordstory
      POSTGRES_USER: mywordstory
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mywordstory -d mywordstory"]
      interval: 5s
      retries: 20

  server:
    image: ghcr.io/normalzero1/mywordstory-server:latest
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      DATABASE_URL: jdbc:postgresql://db:5432/mywordstory
      DATABASE_USER: mywordstory
      DATABASE_PASSWORD: ${DATABASE_PASSWORD}
      API_TOKEN: ${API_TOKEN}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}

  caddy:
    image: caddy:2
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
      - caddy-config:/config

volumes:
  db-data:
  caddy-data:
  caddy-config:
EOF

cat > "$DIR/Caddyfile" <<EOF
${DOMAIN:-:80} {
	reverse_proxy server:8080
}
EOF

cat > "$DIR/backup.sh" <<EOF
#!/usr/bin/env bash
# Daily DB dump to gs://$BUCKET (the bucket deletes files after 7 days).
set -euo pipefail
cd $DIR
docker compose exec -T db pg_dump -U mywordstory mywordstory | gzip > /tmp/mywordstory.sql.gz
gcloud storage cp /tmp/mywordstory.sql.gz "gs://$BUCKET/mywordstory-\$(date +%F).sql.gz"
EOF
chmod 700 "$DIR/backup.sh"
echo "17 4 * * * root $DIR/backup.sh >> /var/log/mywordstory-backup.log 2>&1" > /etc/cron.d/mywordstory-backup

cat > "$DIR/update.sh" <<EOF
#!/usr/bin/env bash
# Pull the newest server image and restart.
set -euo pipefail
cd $DIR
docker compose pull
docker compose up -d
EOF
chmod 700 "$DIR/update.sh"

# Start only once the API key is filled in; then keep it running on every boot.
if grep -q '^ANTHROPIC_API_KEY=.\+' "$DIR/.env"; then
  cd "$DIR" && docker compose up -d || echo "docker compose up failed (has 'docker login ghcr.io' been done?)"
fi
