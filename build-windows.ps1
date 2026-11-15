$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$Python = "python"
if (Test-Path ".venv\Scripts\python.exe") {
    $Python = (Resolve-Path ".venv\Scripts\python.exe").Path
}

$RequiredAssets = @("yolov8n.pt", "alarm.mp3", "faudio.mp3", "paudio.mp3")
foreach ($Asset in $RequiredAssets) {
    if (-not (Test-Path $Asset -PathType Leaf)) {
        throw "Missing required file: $Asset"
    }
}

$ModelFile = Get-Item "yolov8n.pt"
if ($ModelFile.Length -lt 1024 -and
    (Get-Content "yolov8n.pt" -TotalCount 1) -eq "version https://git-lfs.github.com/spec/v1") {
    throw "yolov8n.pt is a Git LFS pointer, not model weights. Install Git LFS and run 'git lfs pull'."
}

function Build-Executable([string]$Name, [string]$EntryPoint) {
    & $Python -m PyInstaller `
        --noconfirm `
        --clean `
        --onefile `
        --name $Name `
        --add-data "yolov8n.pt;." `
        --add-data "alarm.mp3;." `
        --add-data "faudio.mp3;." `
        --add-data "paudio.mp3;." `
        --collect-all ultralytics `
        --collect-all mediapipe `
        --collect-all torchvision `
        $EntryPoint

    if ($LASTEXITCODE -ne 0) {
        throw "PyInstaller failed for $Name with exit code $LASTEXITCODE."
    }
}

Build-Executable "SmartStudyMonitor" "app.py"
Build-Executable "SmartStudyMonitorLite" "app_lite.py"

Write-Host "Original executable: $Root\dist\SmartStudyMonitor.exe"
Write-Host "Lite executable:     $Root\dist\SmartStudyMonitorLite.exe"
