#!/usr/bin/env bash
# Run once in Google Cloud Shell. Creates the free-tier VM, a static IP, a firewall rule
# for HTTP/HTTPS and a backup bucket that keeps 7 days. Needs vm-setup.sh next to it.
#   bash gcp-create.sh word.normalzero.co.kr
set -euo pipefail

DOMAIN="${1:?usage: bash gcp-create.sh <domain, e.g. word.normalzero.co.kr>}"
PROJECT="$(gcloud config get-value project 2>/dev/null)"
[ -n "$PROJECT" ] || { echo "No project selected. Run: gcloud config set project <PROJECT_ID>"; exit 1; }
REGION=us-west1          # free tier: us-west1, us-central1 or us-east1
ZONE=us-west1-b
NAME=mywordstory
BUCKET="$PROJECT-mywordstory-backup"
cd "$(dirname "$0")"
[ -f vm-setup.sh ] || { echo "vm-setup.sh must be in the same folder"; exit 1; }

gcloud services enable compute.googleapis.com storage.googleapis.com

gcloud compute addresses describe "$NAME-ip" --region="$REGION" >/dev/null 2>&1 \
  || gcloud compute addresses create "$NAME-ip" --region="$REGION"
IP="$(gcloud compute addresses describe "$NAME-ip" --region="$REGION" --format='value(address)')"

gcloud compute firewall-rules describe "$NAME-web" >/dev/null 2>&1 \
  || gcloud compute firewall-rules create "$NAME-web" --allow=tcp:80,tcp:443 --target-tags="$NAME"

gcloud storage buckets describe "gs://$BUCKET" >/dev/null 2>&1 \
  || gcloud storage buckets create "gs://$BUCKET" --location="$REGION" --uniform-bucket-level-access
echo '{"rule":[{"action":{"type":"Delete"},"condition":{"age":7}}]}' > /tmp/lifecycle.json
gcloud storage buckets update "gs://$BUCKET" --lifecycle-file=/tmp/lifecycle.json

gcloud compute instances describe "$NAME" --zone="$ZONE" >/dev/null 2>&1 \
  || gcloud compute instances create "$NAME" \
       --zone="$ZONE" --machine-type=e2-micro \
       --image-family=debian-12 --image-project=debian-cloud \
       --boot-disk-size=30GB --boot-disk-type=pd-standard \
       --address="$IP" --tags="$NAME" \
       --scopes=storage-rw,logging-write \
       --metadata="domain=$DOMAIN,backup-bucket=$BUCKET" \
       --metadata-from-file=startup-script=vm-setup.sh

echo
echo "Done. Static IP: $IP"
echo "Next: add a DNS A record  ${DOMAIN%%.*}  ->  $IP  at your domain's DNS settings."
