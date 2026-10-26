@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  campus backend - start all services (serial, one window each)
REM  Run from anywhere; script locates its own folder.
REM  Order matters: common must be installed before any module runs.
REM ============================================================

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "MVN_REPO=D:\apache-maven-3.6.3\MavenData"
set "MVN=mvn"
cd /d "%~dp0"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK not found: %JAVA_HOME%
  goto end
)

echo.
echo === 0. check Nacos 8848 ===
powershell -NoProfile -Command "try { $r=Invoke-WebRequest -Uri 'http://127.0.0.1:8848/nacos/' -TimeoutSec 3 -UseBasicParsing; exit 0 } catch { exit 1 }"
if errorlevel 1 (
  echo [WARN] Nacos is NOT reachable on 8848. Services will fail to register.
  echo        Start Nacos first, then re-run this script.
  goto end
)
echo     Nacos OK

echo.
echo === 1. rebuild campus-common ===
echo     (skip this and you WILL see fake errors like NoClassDefFoundError: LikeMapper)
call %MVN% -q -pl campus-common -DskipTests install -Dmaven.repo.local=%MVN_REPO%
if errorlevel 1 (
  echo [ERROR] campus-common build failed. Stop running services first ^(clean cannot delete locked target^).
  goto end
)
echo     common OK

REM ------------------------------------------------------------
REM  推广功能（campus-promote）本地联调开关。
REM  未开通微信流量主时，真实激励视频广告拉不起来，推广流程走不通。
REM  PROMOTE_MOCK_AD=true 让服务端返回 mockAd，前端用「模拟观看倒计时」
REM  走完整的签发 → 结算链路（最短观看时长校验照旧生效）。
REM  正式投放必须置为 false —— 否则用户不看广告就能刷排序。
REM ------------------------------------------------------------
if not defined PROMOTE_MOCK_AD set "PROMOTE_MOCK_AD=true"
if not defined PROMOTE_AD_UNIT_ID set "PROMOTE_AD_UNIT_ID="

echo.
echo === 2. start services one by one ===
set "MODULES=auth user goods wall post message admin gateway promote"
set /a N=0
for %%m in (%MODULES%) do (
  set /a N+=1
  echo     [!N!] campus-%%m ...
  start "campus-%%m" cmd /k "%MVN% -q -pl campus-%%m spring-boot:run -Dmaven.repo.local=%MVN_REPO%"
  REM stagger: two Maven runs at once race on target/ and on the local repo
  timeout /t 10 /nobreak >nul
)

echo.
echo === 3. waiting for ports ===
set "PORTS=8101 8102 8103 8104 8105 8106 8107 8108 8888"
set /a WAIT=0
set /a UP=0
:wait
set /a WAIT+=1
set /a UP=0
for %%p in (%PORTS%) do (
  netstat -ano | findstr /r /c:":%%p .*LISTENING" >nul && set /a UP+=1
)
echo     round !WAIT! : !UP!/9 up
if !UP! geq 9 goto ready
if !WAIT! geq 30 goto ready
timeout /t 10 /nobreak >nul
goto wait
:ready

echo.
if !UP! geq 9 (
  echo [OK] all 9 services are listening.
) else (
  echo [WARN] only !UP!/9 up - check the individual windows for stack traces.
)
echo.
echo   gateway  http://127.0.0.1:8888/api
echo   admin    http://127.0.0.1:8107
echo   stop     run stop-all.bat
echo.
echo   NOTE: keep this launcher window open; closing it does not stop the
echo         service windows, but a machine reboot will.

:end
echo.
pause
endlocal
