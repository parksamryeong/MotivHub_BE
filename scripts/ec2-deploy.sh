#!/bin/bash
cd ~/MotivHub_BE
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d app
for i in $(seq 1 10); do
  if curl -sf http://localhost/actuator/health/readiness; then
    echo "Deploy healthy"
    exit 0
  fi
  sleep 5
done
echo "Health check failed after deploy"
exit 1
