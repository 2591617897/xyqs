@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  campus backend - stop all services by port
REM  Only kills PIDs that are actually LISTENING on the 10 ports,
REM  so it will not touch Nacos / MySQL / Redis.
REM ============================================================

set "PORTS=8101 8102 8103 8104 8105 8106 8107 8108 8109 8888"

echo.
for %%p in (%PORTS%) do (
  set "FOUND="
  for /f "tokens=5" %%i in ('netstat -ano ^| findstr /r /c:":%%p .*LISTENING"') do (
    set "FOUND=1"
    echo   port %%p  -^> pid %%i  killing...
    taskkill /PID %%i /F >nul 2>&1
  )
  if not defined FOUND echo   port %%p  -^> not running
)

echo.
echo done. ports 8848/3306/6379/5672 untouched.
echo.
pause
endlocal
