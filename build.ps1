# DAVINCI CODE 빌드 스크립트 (Windows PowerShell)
#
#   .\build.ps1              컴파일 → 테스트 → JAR → 실행 파일 패키지(+ WiX가 있으면 설치 프로그램)
#   .\build.ps1 -SkipTests   테스트 없이 빌드
#   .\build.ps1 -JarOnly     JAR까지만 만든다
#
# 결과물은 build\dist 폴더에 생긴다.

param(
    [switch]$SkipTests,
    [switch]$JarOnly
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$version = '1.3.1'
$appName = 'DAVINCI CODE'   # 설치 폴더·바로가기·앱 목록에 보이는 이름
$build = Join-Path $root 'build'
$classes = Join-Path $build 'classes'
$testClasses = Join-Path $build 'test-classes'
$dist = Join-Path $build 'dist'
$jpInput = Join-Path $build 'jpackage-input'
$pkg = Join-Path $build 'package'

function Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }
function Check($what) { if ($LASTEXITCODE -ne 0) { throw "$what 실패 (exit $LASTEXITCODE)" } }

Step '정리'
foreach ($d in @($classes, $testClasses, $dist, $jpInput, $pkg)) {
    if (Test-Path $d) {
        # 실행 중인 설치 프로그램 등이 잡고 있는 파일은 지울 수 없으니 건너뛰고 계속한다
        Get-ChildItem -Force $d | ForEach-Object {
            try { Remove-Item -Recurse -Force $_.FullName -ErrorAction Stop }
            catch { Write-Host "  (사용 중이라 남겨 둠: $($_.TargetObject))" -ForegroundColor Yellow }
        }
    }
    New-Item -ItemType Directory -Force $d | Out-Null
}

Step '컴파일 (Java 17 호환)'
$mainSrc = Get-ChildItem -Recurse (Join-Path $root 'src\main\java') -Filter *.java | ForEach-Object { $_.FullName }
& javac --release 17 -encoding UTF-8 -d $classes $mainSrc
Check '컴파일'
# 웹 게임 파일(HTML/JS/CSS/아이콘)을 JAR에 같이 넣는다. 서버가 브라우저에 이 파일들을 보내 준다
Copy-Item -Recurse -Force (Join-Path $root 'src\main\resources\*') $classes

if (-not $SkipTests) {
    Step '테스트'
    $testSrc = Get-ChildItem -Recurse (Join-Path $root 'src\test\java') -Filter *.java | ForEach-Object { $_.FullName }
    & javac --release 17 -encoding UTF-8 -cp $classes -d $testClasses $testSrc
    Check '테스트 컴파일'
    $cp = "$classes;$testClasses"
    & java '-Dstdout.encoding=UTF-8' -cp $cp stim.davincicode.server.EngineTest
    Check '규칙 엔진 테스트'
    & java '-Dstdout.encoding=UTF-8' -cp $cp stim.davincicode.server.NetTest
    Check '네트워크 통합 테스트'
    & java '-Dstdout.encoding=UTF-8' -cp $cp stim.davincicode.server.WebTest
    Check '웹 접속 테스트'
}

Step 'JAR'
$clientJar = Join-Path $dist 'DavinciCode.jar'
$serverJar = Join-Path $dist 'DavinciCode-Server.jar'
& jar --create --file $clientJar --main-class stim.davincicode.client.ClientMain -C $classes .
Check '앱 JAR'
& jar --create --file $serverJar --main-class stim.davincicode.server.ServerMain -C $classes stim/davincicode/common -C $classes stim/davincicode/server -C $classes stim/davincicode/web
Check '서버 JAR'
Copy-Item (Join-Path $root 'packaging\start-server.bat') $dist
Copy-Item (Join-Path $root 'packaging\start-server.sh') $dist
Write-Host "  $clientJar"
Write-Host "  $serverJar"
if ($JarOnly) { exit 0 }

Step '실행 파일 패키지 (Java 런타임 포함)'
Copy-Item $clientJar $jpInput
$official = Join-Path $root 'official-server.txt'
if (Test-Path $official) {
    Copy-Item $official $jpInput
    Write-Host "  공식 서버 주소 포함: $(Get-Content $official -TotalCount 1)"
}
$common = @(
    '--name', $appName,
    '--app-version', $version,
    '--vendor', 'Team STIM',
    # 설치 프로그램(MSI)은 코드페이지 1252라서 설명에 한글을 넣으면 WiX 오류 311이 난다
    '--description', 'DAVINCI CODE - Number Deduction Battle',
    '--copyright', '(c) 2026 Team STIM',
    '--icon', (Join-Path $root 'packaging\icon.ico'),
    '--input', $jpInput,
    '--main-jar', 'DavinciCode.jar',
    '--main-class', 'stim.davincicode.client.ClientMain',
    '--add-modules', 'java.base,java.desktop,jdk.charsets',
    '--jlink-options', '--strip-debug --no-header-files --no-man-pages --strip-native-commands',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--java-options', '-Xmx256m',
    '--dest', $pkg
)
& jpackage --type app-image @common
Check 'jpackage app-image'
$zip = Join-Path $dist "DavinciCode-$version-windows.zip"
Compress-Archive -Path (Join-Path $pkg $appName) -DestinationPath $zip -Force
Write-Host "  $zip  (압축을 풀고 $appName\$appName.exe 실행)"

# dotnet 도구로 설치한 WiX는 PATH에 아직 없을 수 있어서 직접 찾아 넣는다
$dotnetTools = Join-Path $env:USERPROFILE '.dotnet\tools'
if (-not (Get-Command 'wix.exe' -ErrorAction SilentlyContinue) -and (Test-Path (Join-Path $dotnetTools 'wix.exe'))) {
    $env:PATH = "$env:PATH;$dotnetTools"
}
$hasWix = (Get-Command 'wix.exe' -ErrorAction SilentlyContinue) -or (Get-Command 'candle.exe' -ErrorAction SilentlyContinue)
if ($hasWix) {
    Step '설치 프로그램 (.exe)'
    & jpackage --type exe @common `
        --win-dir-chooser --win-menu --win-menu-group 'Team STIM' --win-shortcut --win-shortcut-prompt `
        --win-per-user-install --win-upgrade-uuid '6b1c3f0e-5a4d-4b8e-9c2a-7d3e1f9a2b44'
    Check 'jpackage exe'
    Get-ChildItem $pkg -Filter *.exe | ForEach-Object { Move-Item $_.FullName $dist -Force; Write-Host "  $(Join-Path $dist $_.Name)" }
} else {
    Write-Host "`n(설치 프로그램 .exe는 WiX Toolset 5가 있어야 만들어져요. README의 '설치 프로그램 만들기'를 참고하세요.)" -ForegroundColor Yellow
}

Step '완료'
Get-ChildItem $dist | Format-Table Name, @{ n = 'MB'; e = { [math]::Round($_.Length / 1MB, 1) } } -AutoSize
