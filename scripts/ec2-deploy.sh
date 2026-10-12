#!/bin/bash
# GitHub Actions가 AWS SSM으로 이 스크립트를 root 권한으로 실행한다 - "~"는 root의 홈(/root)으로
# 풀리는데, 실제 체크아웃은 ec2-user 홈에 있어서 상대/틸드 경로로 쓰면 엉뚱한 디렉터리로 이동한다.
# 반드시 절대경로를 써야 한다.
set -e
cd /home/ec2-user/MotivHub_BE
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
