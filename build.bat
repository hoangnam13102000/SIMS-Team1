@echo off
REM ============================================================
REM  build.bat - Dong goi SIMS thanh file .exe (Windows)
REM  Cach dung: copy file nay vao THU MUC GOC cua project SIMS
REM  (cung cap voi pom.xml), roi double-click de chay.
REM  Yeu cau: da cai JDK 17 va Maven, va da them vao PATH.
REM ============================================================

setlocal enabledelayedexpansion



set APP_NAME=SIMS
set MAIN_CLASS=com.Main
set APP_VERSION=1.0
REM Doi ten icon neu ban co file .ico rieng, de trong ("") neu khong co icon
set ICON_PATH=src\main\resources\logo\logo_icon.ico

REM PACKAGE_TYPE:
REM   app-image -> thu muc portable, chi can giai nen la chay, khong can cai dat
REM   exe       -> file installer .exe (co man hinh Setup, tao shortcut, ghi vao
REM                Add/Remove Programs). Can cai WiX Toolset v3 truoc (xem README).
set PACKAGE_TYPE=exe

echo.
echo [1/4] Kiem tra Java va Maven...
where java >nul 2>nul
if errorlevel 1 (
    echo LOI: Khong tim thay 'java'. Hay cai JDK 17 va them vao PATH.
    pause
    exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo LOI: Khong tim thay 'mvn'. Hay cai Maven va them vao PATH.
    pause
    exit /b 1
)
if /i "%PACKAGE_TYPE%"=="exe" (
    where candle >nul 2>nul
    if errorlevel 1 (
        echo LOI: Khong tim thay WiX Toolset ^(candle.exe^).
        echo Tao installer .exe can cai WiX Toolset v3 va them vao PATH.
        echo Tai tai: https://wixtoolset.org/releases/  ^(chon ban v3.x, KHONG dung v4/v5^)
        echo Hoac doi PACKAGE_TYPE=app-image o dau file nay neu chi can ban portable.
        pause
        exit /b 1
    )
)

echo.
echo [2/4] Build fat-jar bang Maven (mvn clean package)...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo LOI: Build Maven that bai. Xem log ben tren de sua loi.
    pause
    exit /b 1
)

REM Tim file fat-jar (shaded) vua build trong thu muc target.
REM QUAN TRONG: maven-shade-plugin tao ra 2 file jar trong target:
REM   - sims-0.0.1-SNAPSHOT.jar          -> fat jar, co day du dependency (file nay can dung)
REM   - original-sims-0.0.1-SNAPSHOT.jar -> jar goc, KHONG co dependency nao (chi la backup)
REM Neu dua ca thu muc target cho jpackage (--input target), jpackage se thay ca 2 jar
REM va ghi de classpath trong SIMS.cfg thanh original-*.jar (jar rong) -> app crash khi mo,
REM khong hien loi gi vi la ung dung Swing khong co console. Vi vay phai loc bo file
REM original-*.jar va chi dong goi (staging) rieng file fat-jar vao mot thu muc sach.
set JAR_FILE=
for %%f in (target\*.jar) do (
    echo %%~nf | findstr /i "sources javadoc original-" >nul
    if errorlevel 1 (
        set JAR_FILE=%%f
    )
)

if "%JAR_FILE%"=="" (
    echo LOI: Khong tim thay file fat-jar trong thu muc target.
    pause
    exit /b 1
)
echo Da tim thay jar: %JAR_FILE%

REM Tao thu muc staging rieng, CHI chua fat-jar, de jpackage khong bi lan sang file jar khac
REM (va cung khong keo theo test-classes, generated-sources... khong can thiet vao ban dong goi)
set STAGE_DIR=target\jpackage-input
if exist "%STAGE_DIR%" rmdir /s /q "%STAGE_DIR%"
mkdir "%STAGE_DIR%"
copy /y "%JAR_FILE%" "%STAGE_DIR%\" >nul



REM ============================================================
REM Dong goi thu muc uploads vao installer
REM ============================================================
REM Dong goi anh mac dinh (uploads) cung voi app.
REM Anh upload moi sau khi cai se duoc luu vao %%LOCALAPPDATA%%\SIMS\uploads.
if exist "uploads" (
    xcopy /e /i /y "uploads" "%STAGE_DIR%\uploads" >nul
    if errorlevel 1 (
        echo CANH BAO: Khong copy duoc thu muc uploads.
    ) else (
        echo Da dong goi thu muc uploads.
    )
) else (
    echo CANH BAO: Khong tim thay thu muc uploads.
)

echo.
echo [3/4] Dong goi thanh file .exe bang jpackage...
echo (Dung toan bo JDK dang cai lam runtime, de tranh thieu module)

REM ============================================================
REM Doc MYSHOP_CONFIG_KEY tu file "config.key" (KHONG commit file nay
REM vao Git - them "config.key" vao .gitignore). File chi chua 1 dong
REM la chuoi key base64 sinh boi ConfigTool genkey. Key se duoc "bake"
REM thang vao <APP_NAME>.cfg cua ban .exe qua --java-options, nen nguoi
REM dung cuoi KHONG can tu set bien moi truong sau khi cai app.
REM ============================================================
if not exist "config.key" (
    echo LOI: Khong tim thay file config.key o thu muc goc.
    echo Tao file nay ^(1 dong, khong xuong dong thua^) bang key da sinh tu:
    echo   java -cp target\classes com.security.tool.ConfigTool genkey
    pause
    exit /b 1
)
set /p CONFIG_KEY=<config.key

REM Xoa ban dong goi cu (neu co) de tranh jpackage gop lan voi file cu con sot lai
if exist "dist\%APP_NAME%" rmdir /s /q "dist\%APP_NAME%"

for /f "delims=" %%j in ('where java') do set JAVA_BIN=%%j
for %%i in ("%JAVA_BIN%\..\..") do set JAVA_HOME_DETECTED=%%~fi

set JP_ARGS=--type %PACKAGE_TYPE% --name %APP_NAME% --input "%STAGE_DIR%" --main-jar "%JAR_FILE:target\=%" --main-class %MAIN_CLASS% --runtime-image "%JAVA_HOME_DETECTED%" --app-version %APP_VERSION% --vendor "SIMS" --dest dist --java-options "-Dmyshop.config.key=%CONFIG_KEY%"

if not "%ICON_PATH%"=="" (
    set JP_ARGS=%JP_ARGS% --icon "%ICON_PATH%"
)

REM Cac tuy chon chi co tac dung khi tao installer (--type exe/msi), jpackage se
REM tu bo qua neu PACKAGE_TYPE=app-image.
if /i "%PACKAGE_TYPE%"=="exe" (
    set JP_ARGS=%JP_ARGS% --win-shortcut --win-menu --win-dir-chooser
)

jpackage %JP_ARGS%
if errorlevel 1 (
    echo LOI: jpackage that bai. Xem log ben tren.
    pause
    exit /b 1
)

echo.
echo [4/4] Hoan tat!
if /i "%PACKAGE_TYPE%"=="exe" (
    echo File installer nam trong thu muc: dist\
    echo Chay file: dist\%APP_NAME%-%APP_VERSION%.exe de cai dat.
) else (
    echo Ung dung portable nam trong thu muc: dist\%APP_NAME%\
    echo Chay file: dist\%APP_NAME%\%APP_NAME%.exe
    echo.
    echo (Muon tao file cai dat .exe thay vi thu muc portable, mo file nay
    echo  bang Notepad va doi "set PACKAGE_TYPE=app-image" thanh "set PACKAGE_TYPE=exe" o tren.)
)
echo.
pause