#!/usr/bin/env bash
# 临时脚本：重启受推广功能影响的服务（新增字段 + 内部接口 + 网关路由），并启动 campus-promote
set -u
cd /f/校园趣事/backend
export JAVA_HOME='C:\Program Files\Java\jdk-17'
MVN=/d/apache-maven-3.6.3/apache-maven-3.6.3/bin/mvn
REPO='D:\apache-maven-3.6.3\MavenData'
export PROMOTE_MOCK_AD=true

for pid in 39260 42668 6760 21880; do
  taskkill //PID $pid //F >/dev/null 2>&1
done
sleep 3

for m in promote goods wall post gateway; do
  echo "[start] campus-$m"
  env -u SERVER__PORT "$MVN" -q -pl "campus-$m" spring-boot:run -Dmaven.repo.local="$REPO" > "/tmp/campus-$m.log" 2>&1 &
  sleep 18
done

echo "[all launched]"
wait
