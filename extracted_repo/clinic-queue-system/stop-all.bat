@echo off
setlocal EnableDelayedExpansion
title Clinic Queue System - Shutdown

REM ===========================================================================
REM  stop-all.bat  -  stops everything start-all.bat launched
REM ===========================================================================
REM
REM  The backend and frontend run in their own consoles, so closing those
REM  windows already stops them. PostgreSQL is different: pg_ctl detaches the
REM  postmaster, so it survives both the launcher window and a reboot of the
REM  consoles. This script stops all three.
REM
REM  Processes are matched by COMMAND LINE, never by image name. Killing every
REM  java.exe / node.exe would also kill unrelated projects on this machine
REM  (the mall-management backend, the Vite dev server on :5173, ...), which is
REM  exactly the kind of collateral damage a shutdown script must not cause.
REM ===========================================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "DB_PORT=5433"

echo.
echo  ============================================================
echo   Clinic Queue System - stopping services
echo  ============================================================
echo.

REM ---------------------------------------------------------------------------
REM  1. Backend  -  the java process running this project's jar
REM ---------------------------------------------------------------------------
echo  [1/3] Backend...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { $_.CommandLine -like '*clinic-queue-backend*' }; if ($p) { $p | ForEach-Object { Write-Host ('        stopping java PID ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force } } else { Write-Host '        not running' }"

REM ---------------------------------------------------------------------------
REM  2. Frontend  -  the node process serving this project's Vite dev server
REM ---------------------------------------------------------------------------
echo  [2/3] Frontend...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = Get-CimInstance Win32_Process -Filter \"Name='node.exe'\" | Where-Object { $_.CommandLine -like '*clinic-queue-system*' -and $_.CommandLine -like '*vite*' }; if ($p) { $p | ForEach-Object { Write-Host ('        stopping node PID ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force } } else { Write-Host '        not running' }"

REM ---------------------------------------------------------------------------
REM  3. PostgreSQL  -  fast shutdown of the cluster on port 5433
REM ---------------------------------------------------------------------------
echo  [3/3] PostgreSQL on :%DB_PORT%...
set "PG_CTL="
for %%P in (pg_ctl.exe) do if not defined PG_CTL set "PG_CTL=%%~$PATH:P"
if not defined PG_CTL if exist "C:\Users\%USERNAME%\pg16\bin\pg_ctl.exe" set "PG_CTL=C:\Users\%USERNAME%\pg16\bin\pg_ctl.exe"
if not defined PG_CTL if exist "C:\Program Files\PostgreSQL\16\bin\pg_ctl.exe" set "PG_CTL=C:\Program Files\PostgreSQL\16\bin\pg_ctl.exe"

set "PG_BIN="
set "PG_DATA="
if not defined PG_CTL goto :PG_NOT_FOUND

REM No parenthesised block here on purpose: a variable set inside a block is not
REM visible to %VAR% expansion in that same block (the block is parsed as a
REM whole before it runs), which silently produced empty paths. Delayed
REM expansion (!VAR!) is used for the derived values instead.
for %%I in ("%PG_CTL%") do set "PG_BIN=%%~dpI"
if "!PG_BIN:~-1!"=="\" set "PG_BIN=!PG_BIN:~0,-1!"
if exist "!PG_BIN!\..\data\PG_VERSION" set "PG_DATA=!PG_BIN!\..\data"
if not defined PG_DATA if exist "!PG_BIN!\data\PG_VERSION" set "PG_DATA=!PG_BIN!\data"

if not defined PG_DATA goto :PG_NO_DATA

REM Only stop it if it is actually running on our port.
"!PG_BIN!\pg_isready.exe" -p %DB_PORT% >nul 2>&1
if errorlevel 1 goto :PG_NOT_RUNNING

"%PG_CTL%" -D "!PG_DATA!" -m fast stop
goto :PG_DONE

REM `else if` chains are not reliable in cmd.exe - they break with
REM "The syntax of the command is incorrect" depending on the block content,
REM so each branch is a label instead.
:PG_NOT_FOUND
echo         pg_ctl not found - is PostgreSQL installed?
goto :PG_DONE

:PG_NO_DATA
echo         pg_ctl found but no data directory - not touching it.
goto :PG_DONE

:PG_NOT_RUNNING
echo         not running on :%DB_PORT%

:PG_DONE

echo.
echo  ============================================================
echo   Done. Remaining console windows can be closed.
echo  ============================================================
echo.
exit /b 0
