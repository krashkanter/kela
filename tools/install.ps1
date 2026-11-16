param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
function Invoke-Adb {
    & $Adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed ($LASTEXITCODE)" }
}
if (-not $SkipBuild) {
    Push-Location $project
    try { & .\gradlew.bat :app:assembleDebug --console=plain; if ($LASTEXITCODE -ne 0) { throw 'Build failed' } }
    finally { Pop-Location }
}
Invoke-Adb shell mkdir -p /data/local/debian/opt/kela
Invoke-Adb push "$project\server\kela_server.py" /data/local/debian/opt/kela/kela_server.py
Invoke-Adb push "$project\server\start-bridge.sh" /data/local/server/start-kela.sh
Invoke-Adb shell chmod 700 /data/local/server/start-kela.sh
Invoke-Adb push "$project\server\stop-bridge.sh" /data/local/server/stop-kela.sh
Invoke-Adb shell chmod 700 /data/local/server/stop-kela.sh
Invoke-Adb shell sh /data/local/server/stop-kela.sh
Invoke-Adb shell sh /data/local/server/start-kela.sh
Invoke-Adb push "$project\server\install-hook.sh" /data/local/tmp/kela-install-hook.sh
Invoke-Adb shell sh /data/local/tmp/kela-install-hook.sh
Invoke-Adb install -r "$project\app\build\outputs\apk\debug\app-debug.apk"
# Token moves only inside the phone, straight into Android's private app storage.
Invoke-Adb push "$project\server\pair.sh" /data/local/tmp/kela-pair.sh
Invoke-Adb shell sh /data/local/tmp/kela-pair.sh
Invoke-Adb shell am start -n com.krashkanter.kela/.MainActivity