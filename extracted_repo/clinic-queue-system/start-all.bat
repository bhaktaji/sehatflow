@echo off
setlocal EnableDelayedExpansion
title Clinic Queue System - Launcher

REM ===========================================================================
REM  start-all.bat  -  one-click local launcher for the Clinic Queue System
REM ===========================================================================
REM
REM  Starts, in order:
REM    1. PostgreSQL 16   (native local server, port 5433 - NO Docker)
REM    2. Spring Boot     (Maven + JDK 21, port 8090)
REM    3. Vite            (React dev server, port 5180)
REM  then opens the browser once the frontend is actually listening.
REM
REM  The backend and frontend each get their own console window so their logs
REM  stay readable and closing one does not disturb the other. PostgreSQL runs
REM  as a detached server - use stop-all.bat to shut it down.
REM
REM  ---------------------------------------------------------------------------
REM  THREE THINGS THIS SCRIPT DOES NOT ASSUME, BECAUSE ASSUMING THEM BREAKS IT
REM  ---------------------------------------------------------------------------
REM
REM  1. THE `java` ON PATH IS A JRE 8 - IT CANNOT BUILD THIS PROJECT.
REM     The project targets Java 21, and JAVA_HOME on this machine points at
REM     "Eclipse Adoptium\jre-8.0.504.1-hotspot" (a JRE: no javac). Running Maven
REM     with that JAVA_HOME fails with "No compiler is provided in this
REM     environment". So this script LOCATES a JDK 21 and exports JAVA_HOME for
REM     the backend window instead of trusting the ambient value.
REM     Resolution order: JAVA_HOME (if it is a JDK >= 21) -> Microsoft ->
REM     Adoptium -> Oracle -> ~\.jdks (IntelliJ) -> fail with instructions.
REM
REM  2. POSTGRESQL MUST LISTEN ON PORT 5433, NOT THE DEFAULT 5432.
REM     application.yml points at jdbc:postgresql://localhost:5433/clinicqueue.
REM     A default `pg_ctl start` brings the server up on 5432 and the backend
REM     then fails with "Connection to localhost:5433 refused", so the server is
REM     always started with -o "-p 5433".
REM
REM  3. pg_ctl IS NOT ON PATH.
REM     It lives under the local PostgreSQL 16 install (C:\Users\<you>\pg16\bin
REM     on this machine). This script probes PATH first, then the usual install
REM     roots, and stops with a clear message if it cannot find one.
REM
REM  ---------------------------------------------------------------------------
REM  OPTIONAL SWITCHES
REM  ---------------------------------------------------------------------------
REM    start-all.bat --rebuild      run `mvn clean package` before starting
REM    start-all.bat --no-build     use the existing jar, do not compile
REM    start-all.bat --no-browser   do not open the browser at the end
REM
REM  By default the backend is compiled incrementally (mvn -DskipTests package,
REM  ~10s warm) so the jar is never stale. Tests are not run here - use
REM  scripts\backend-test.sh for that.
REM ===========================================================================


REM ---------------------------------------------------------------------------
REM  Resolve paths from this script's own location, so the launcher works no
REM  matter which directory it is invoked from (including double-click).
REM ---------------------------------------------------------------------------
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "BACKEND=%ROOT%\backend"
set "FRONTEND=%ROOT%\frontend"
set "JAR=%BACKEND%\target\clinic-queue-backend-0.1.0.jar"

REM Ports are pinned in three places: vite.config.ts (5180, strictPort),
REM the dev proxy target (8090) and the backend launch argument below.
REM Change all three together or the SPA will talk to nothing.
set "BACKEND_PORT=8090"
set "FRONTEND_PORT=5180"
set "DB_PORT=5433"
set "FRONTEND_URL=http://localhost:%FRONTEND_PORT%"
set "BACKEND_HEALTH=http://localhost:%BACKEND_PORT%/actuator/health"

REM Parse switches.
set "REBUILD=0"
set "SKIP_BUILD=0"
set "OPEN_BROWSER=1"
:PARSE_ARGS
if "%~1"=="" goto :ARGS_DONE
if /i "%~1"=="--rebuild" set "REBUILD=1"
if /i "%~1"=="--no-build" set "SKIP_BUILD=1"
if /i "%~1"=="--no-browser" set "OPEN_BROWSER=0"
shift
goto :PARSE_ARGS
:ARGS_DONE

echo.
echo  ============================================================
echo   Clinic Queue System - local launcher
echo  ============================================================
echo   Root     : %ROOT%
echo   Backend  : %BACKEND%  ^(:%BACKEND_PORT%^)
echo   Frontend : %FRONTEND%  ^(:%FRONTEND_PORT%^)
echo   Database : PostgreSQL on :%DB_PORT%
echo.

REM ---------------------------------------------------------------------------
REM  Fail fast: verify the tree before opening a single window.
REM ---------------------------------------------------------------------------
if not exist "%BACKEND%\pom.xml" (
    echo  [ERROR] Not found: "%BACKEND%\pom.xml"
    echo          Run this script from the repository root, or move it back there.
    goto :fail
)
if not exist "%FRONTEND%\package.json" (
    echo  [ERROR] Not found: "%FRONTEND%\package.json"
    echo          The frontend directory is missing or incomplete.
    goto :fail
)


REM ===========================================================================
REM  0. Locate a JDK 21  (see note 1 in the header - java on PATH is a JRE 8)
REM ===========================================================================
set "JDK_HOME="

REM --- 0a. An existing JAVA_HOME only counts if it is a real JDK and >= 21.
REM         `javac.exe` is the discriminator: JREs do not ship one.
call :CHECK_JDK "%JAVA_HOME%"
if defined JDK_HOME echo  [OK]    JDK found via JAVA_HOME: !JDK_HOME!

REM --- 0b. Known vendor install locations. `for /d` keeps this tolerant of
REM         patch-level folder names (jdk-21.0.12.101-hotspot, jdk-21.0.5, ...).
if not defined JDK_HOME call :SCAN_JDK_ROOT "C:\Program Files\Microsoft"
if not defined JDK_HOME call :SCAN_JDK_ROOT "C:\Program Files\Eclipse Adoptium"
if not defined JDK_HOME call :SCAN_JDK_ROOT "C:\Program Files\Java"
if not defined JDK_HOME call :SCAN_JDK_ROOT "%USERPROFILE%\.jdks"

if not defined JDK_HOME (
    echo.
    echo  [ERROR] No JDK 21 could be located.
    echo.
    echo          `java -version` reports a JRE 8, and a JRE cannot compile this
    echo          project ^(it targets Java 21 and has no javac^).
    echo.
    echo          Fix it with EITHER of these:
    echo            a^) Install a JDK 21 and set JAVA_HOME to it:
    echo               https://adoptium.net/temurin/releases/?version=21
    echo            b^) Point JAVA_HOME at an existing JDK 21 install, e.g.
    echo               setx JAVA_HOME "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
    echo.
    goto :fail
)

REM Export it for every child console this script opens.
set "JAVA_HOME=%JDK_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo  [OK]    JAVA_HOME for this run: %JAVA_HOME%


REM ===========================================================================
REM  1. Locate PostgreSQL  (see note 3 - pg_ctl is not on PATH here)
REM ===========================================================================
set "PG_BIN="
set "PG_DATA="
set "PG_CTL="

REM --- 1a. PATH first: a properly installed PostgreSQL usually is.
for %%P in (pg_ctl.exe) do if not defined PG_CTL set "PG_CTL=%%~$PATH:P"

REM --- 1b. Common install roots. The data directory is probed next to the
REM         binaries (..\data) and inside them (bin\data) because both layouts
REM         are common for the zip distribution.
if not defined PG_CTL call :SCAN_PG "C:\Users\%USERNAME%\pg16"
if not defined PG_CTL call :SCAN_PG "C:\Users\%USERNAME%\pgsql"
if not defined PG_CTL call :SCAN_PG "C:\Program Files\PostgreSQL\16"
if not defined PG_CTL call :SCAN_PG "C:\Program Files\PostgreSQL"

if not defined PG_CTL (
    echo.
    echo  [ERROR] PostgreSQL ^(pg_ctl.exe^) could not be located.
    echo.
    echo          This project needs a local PostgreSQL 16 on port %DB_PORT%.
    echo          Install it, or set PG_CTL / PG_DATA at the top of this script.
    echo.
    goto :fail
)
if not defined PG_DATA (
    echo.
    echo  [ERROR] PostgreSQL binaries found at "%PG_BIN%", but no data directory.
    echo          Expected one of: "%PG_BIN%\..\data" or "%PG_BIN%\data".
    echo.
    echo          Set PG_DATA manually in this script to point at your cluster.
    echo.
    goto :fail
)
echo  [OK]    PostgreSQL binaries: %PG_BIN%
echo  [OK]    PostgreSQL data dir: %PG_DATA%


REM ===========================================================================
REM  2. PostgreSQL  -  native server on port %DB_PORT%  (see note 2)
REM ===========================================================================
echo.
echo  [1/4] Starting PostgreSQL on :%DB_PORT% ...

set "PGPASSWORD=postgres"
set "PSQL=%PG_BIN%\psql.exe"
set "PG_ISREADY=%PG_BIN%\pg_isready.exe"

"%PG_ISREADY%" -p %DB_PORT% >nul 2>&1
if not errorlevel 1 (
    echo         Already running - reusing it.
    goto :DB_READY
)

REM `-o "-p 5433"` is the whole point: without it the server comes up on 5432
REM and the backend cannot connect. pg_ctl returns as soon as the postmaster is
REM accepting connections, so this does not block the launcher.
if not exist "%PG_DATA%\PG_VERSION" (
    echo  [ERROR] "%PG_DATA%" does not look like a PostgreSQL data directory.
    echo          ^(PG_VERSION is missing - run initdb, or fix PG_DATA.^)
    goto :fail
)

"%PG_CTL%" -D "%PG_DATA%" -l "%PG_DATA%\..\pg.log" -o "-p %DB_PORT%" start
if errorlevel 1 (
    echo.
    echo  [ERROR] pg_ctl failed to start the server.
    echo          Log: %PG_DATA%\..\pg.log
    goto :fail
)

REM Wait for the socket rather than sleeping blindly.
set /a PROBES=0
:WAIT_FOR_PG
"%PG_ISREADY%" -p %DB_PORT% >nul 2>&1
if not errorlevel 1 goto :DB_READY
set /a PROBES+=1
if %PROBES% GEQ 20 (
    echo  [ERROR] PostgreSQL did not accept connections within 20s.
    echo          Log: %PG_DATA%\..\pg.log
    goto :fail
)
call :SLEEP 1
goto :WAIT_FOR_PG

:DB_READY
echo         PostgreSQL is accepting connections on :%DB_PORT%.

REM Create the two databases on first run. Flyway builds the schema itself;
REM only the databases have to exist.
for %%D in (clinicqueue clinicqueue_test) do (
    "%PSQL%" -p %DB_PORT% -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='%%D'" 2>nul | findstr /r "^1$" >nul
    if errorlevel 1 (
        echo         Creating database %%D ...
        "%PSQL%" -p %DB_PORT% -U postgres -d postgres -c "CREATE DATABASE %%D" >nul 2>&1
        if errorlevel 1 (
            echo  [WARN] Could not create database %%D. Check the postgres password.
            echo         The backend expects user "postgres" / password "postgres"
            echo         unless APP_DB_USER / APP_DB_PASSWORD are set.
        ) else (
            echo         Created %%D.
        )
    )
)


REM ===========================================================================
REM  3. Spring Boot backend  -  build if needed, then run the jar
REM ===========================================================================
echo.
echo  [2/4] Starting Spring Boot backend on :%BACKEND_PORT% ...

if "%SKIP_BUILD%"=="1" (
    if not exist "%JAR%" (
        echo  [ERROR] --no-build was given but the jar does not exist:
        echo          %JAR%
        echo          Run once without --no-build.
        goto :fail
    )
    echo         --no-build: using the existing jar.
    goto :BACKEND_CMD_READY
)

if "%REBUILD%"=="1" (
    set "MVN_GOAL=clean package"
) else (
    set "MVN_GOAL=package"
)
echo         Building: mvn -DskipTests %MVN_GOAL%   ^(in the backend window^)

:BACKEND_CMD_READY

REM The backend window runs build-then-run in one shell, so a compile error is
REM visible in that window and the server never starts against a stale jar.
REM /k keeps the window open on failure so the error can be read.
REM
REM The jar path is RELATIVE on purpose: the working directory is already the
REM backend folder (/D above), and a relative path contains no spaces, so no
REM quotes are needed inside the /k argument. Nesting quotes there is fragile -
REM cmd.exe strips the outer pair conditionally and breaks when the inner path
REM has spaces.
if "%SKIP_BUILD%"=="1" (
    start "Backend Server" /D "%BACKEND%" cmd /k "java -jar target\clinic-queue-backend-0.1.0.jar --server.port=%BACKEND_PORT%"
) else (
    start "Backend Server" /D "%BACKEND%" cmd /k "mvn -DskipTests %MVN_GOAL% && java -jar target\clinic-queue-backend-0.1.0.jar --server.port=%BACKEND_PORT%"
)


REM ===========================================================================
REM  4. Vite frontend  -  install only when node_modules is missing
REM ===========================================================================
echo  [3/4] Starting Vite frontend on :%FRONTEND_PORT% ...

REM node_modules is present in a normal working tree, so installing on every
REM launch would cost minutes for nothing. The install branch runs only on a
REM fresh clone or after a dependency change cleared it.
start "Frontend Server" /D "%FRONTEND%" cmd /k "if exist node_modules (npm run dev) else (npm install && npm run dev)"


REM ===========================================================================
REM  5. Wait for the services, then open the browser
REM ===========================================================================
echo  [4/4] Waiting for the services to come up...

REM Give the JVM a head start before probing. >nul is required: timeout refuses
REM to run in a redirected context otherwise.
call :SLEEP 8

where curl >nul 2>&1
if errorlevel 1 (
    echo  [WARN] curl not available - skipping the readiness checks.
    goto :OPEN_BROWSER
)

REM --- Frontend: 45 probes at 2s caps the wait at 90s (Vite is fast, but the
REM     first `npm install` on a fresh clone is not).
set /a PROBES=0
:WAIT_FOR_FRONTEND
set /a PROBES+=1
curl -s -o nul --max-time 2 "%FRONTEND_URL%" >nul 2>&1
if not errorlevel 1 (
    echo         Frontend responded after %PROBES% probe^(s^).
    goto :CHECK_BACKEND
)
if %PROBES% GEQ 45 (
    echo  [WARN] The frontend did not respond within 90s.
    echo         Check the "Frontend Server" window for a compile error or a
    echo         port conflict on %FRONTEND_PORT%.
    goto :OPEN_BROWSER
)
call :SLEEP 2
goto :WAIT_FOR_FRONTEND

:CHECK_BACKEND
REM --- Backend: the SPA loads without it, but every screen would be empty, so
REM     report it explicitly instead of letting the user wonder.
set /a PROBES=0
:WAIT_FOR_BACKEND
curl -s -o nul --max-time 2 "%BACKEND_HEALTH%" >nul 2>&1
if not errorlevel 1 (
    echo         Backend healthy after %PROBES% probe^(s^).
    goto :OPEN_BROWSER
)
set /a PROBES+=1
if %PROBES% GEQ 60 (
    echo  [WARN] The backend is not healthy yet ^(first build can take a while^).
    echo         Watch the "Backend Server" window - the app will work as soon
    echo         as it reports "Started ClinicQueueApplication".
    goto :OPEN_BROWSER
)
call :SLEEP 2
goto :WAIT_FOR_BACKEND

:OPEN_BROWSER
if "%OPEN_BROWSER%"=="1" (
    echo.
    echo  Opening %FRONTEND_URL%
    REM The empty "" is the window title argument - without it, `start` treats
    REM the URL as the title and opens nothing at all.
    start "" "%FRONTEND_URL%"
)

echo.
echo  ============================================================
echo   Stack starting. Two windows were opened:
echo     "Backend Server"   - Spring Boot on :%BACKEND_PORT%
echo     "Frontend Server"  - Vite on :%FRONTEND_PORT%
echo.
echo   App      %FRONTEND_URL%
echo   API      %BACKEND_HEALTH%
echo   Swagger  http://localhost:%BACKEND_PORT%/swagger-ui.html
echo   Database PostgreSQL on :%DB_PORT% ^(detached - stop-all.bat^)
echo.
echo   Login: owner / owner123  ^|  reception / recep123  ^|  doctor / doc123
echo   First run seeds the demo clinic "Sunrise Family Clinic".
echo  ============================================================
echo.
echo  This window can be closed. The services keep running in theirs.
REM A plain `exit /b` here - NOT `exit` - so double-clicking the script closes
REM this window instead of terminating the console hosting the other services.
exit /b 0


REM ===========================================================================
REM  Subroutines
REM ===========================================================================

REM :SLEEP <seconds>
REM Windows' own timeout.exe is avoided on purpose:
REM   * a GNU `timeout` earlier on PATH (Git for Windows, MSYS) rejects /t and
REM     floods the console with "invalid time interval";
REM   * timeout.exe itself refuses to run when stdin is redirected.
REM ping.exe is always present, always the Windows one when called by absolute
REM path, and sleeps for (count-1) seconds between its own two probes.
:SLEEP
set /a SLEEP_N=%~1+1
"%SystemRoot%\System32\ping.exe" -n %SLEEP_N% 127.0.0.1 >nul 2>&1
goto :eof

REM :CHECK_JDK <candidate home>
REM Accepts the candidate only when bin\javac.exe exists (a real JDK) and the
REM reported major version is >= 21. Sets JDK_HOME on success.
REM
REM javac is invoked from INSIDE its own bin directory on purpose. Feeding a
REM quoted absolute path with spaces to `for /f ... in ('...')` is a classic
REM cmd.exe trap: the single-quoted command gets mis-parsed and the loop yields
REM nothing, which would silently reject every JDK on this machine.
:CHECK_JDK
set "CAND=%~1"
if "%CAND%"=="" goto :eof
if not exist "%CAND%\bin\javac.exe" goto :eof
set "MAJOR="
pushd "%CAND%\bin"
REM delims is ". " (dot AND space) on purpose. With only "." the line
REM "javac 21.0.12" splits as ["javac 21"]["0"]["12"], so token 2 is "0" and
REM every JDK looks too old. With both delimiters it is ["javac"]["21"]["0"]["12"].
for /f "tokens=2 delims=. " %%V in ('javac -version 2^>^&1') do set "MAJOR=%%V"
popd
if not defined MAJOR (
    REM Fallback: read the version out of the JDK's release file.
    findstr /r /c:"JAVA_VERSION=.21" "%CAND%\release" >nul 2>&1
    if not errorlevel 1 set "JDK_HOME=%CAND%"
    goto :eof
)
REM javac 8 reports "javac 1.8.0_504" -> token 2 is "8"; javac 21 reports "21.0.12".
if "%MAJOR%"=="1" goto :eof
if %MAJOR% LSS 21 goto :eof
set "JDK_HOME=%CAND%"
goto :eof

REM :SCAN_JDK_ROOT <root directory>
REM Looks for jdk-21* / jdk21* / *jdk-21* folders under the root and hands the
REM first one that passes CHECK_JDK to JDK_HOME.
:SCAN_JDK_ROOT
set "ROOT_DIR=%~1"
if not exist "%ROOT_DIR%\" goto :eof
for /d %%D in ("%ROOT_DIR%\*jdk*21*") do (
    if not defined JDK_HOME call :CHECK_JDK "%%~fD"
)
for /d %%D in ("%ROOT_DIR%\*21*") do (
    if not defined JDK_HOME call :CHECK_JDK "%%~fD"
)
goto :eof

REM :SCAN_PG <root directory>
REM Finds pg_ctl.exe under <root>\bin or <root>\*\bin and derives PG_DATA from
REM the layout next to the binaries.
:SCAN_PG
set "PG_ROOT=%~1"
if not exist "%PG_ROOT%\" goto :eof
if exist "%PG_ROOT%\bin\pg_ctl.exe" (
    set "PG_BIN=%PG_ROOT%\bin"
    if exist "%PG_ROOT%\data\PG_VERSION" set "PG_DATA=%PG_ROOT%\data"
    if not defined PG_DATA if exist "%PG_ROOT%\bin\data\PG_VERSION" set "PG_DATA=%PG_ROOT%\bin\data"
    set "PG_CTL=%PG_ROOT%\bin\pg_ctl.exe"
    goto :eof
)
for /d %%D in ("%PG_ROOT%\*") do (
    if not defined PG_CTL if exist "%%~fD\bin\pg_ctl.exe" (
        set "PG_BIN=%%~fD\bin"
        if exist "%%~fD\data\PG_VERSION" set "PG_DATA=%%~fD\data"
        if not defined PG_DATA if exist "%%~fD\bin\data\PG_VERSION" set "PG_DATA=%%~fD\bin\data"
        set "PG_CTL=%%~fD\bin\pg_ctl.exe"
    )
)
goto :eof


:fail
echo.
echo  Launch aborted.
echo.
pause
exit /b 1
