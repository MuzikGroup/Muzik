# Adds a "launch Muzik after successful install" action to a jpackage MSI.
#
# jpackage has no built-in launch-after-install support, so this edits the MSI
# database directly with the WindowsInstaller COM API (no WiX dependency, and
# unlike .idt imports this MERGES rows instead of replacing whole tables).
#
# What gets added (fresh installs only, right after InstallFinalize):
#   JpSetLaunchCmd (type 51):  JpLaunchCmd = [%WINDIR]\explorer.exe
#   JpLaunchApp   (type 178):  runs JpLaunchCmd with args "[INSTALLDIR]Muzik.exe"
#
# Launching through explorer.exe is deliberate: the interactive install runs
# elevated, and starting Muzik.exe directly would inherit the administrator
# token. Explorer always runs as the logged-in desktop user, so the app starts
# with normal permissions. The async return type keeps the installer unblocked.
#
# Usage:  ./msi-launch-after-install.ps1 -MsiPath <file.msi>
# The script is idempotent: re-running it on an already-patched MSI is a no-op.

param(
    [Parameter(Mandatory = $true)][string]$MsiPath
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $MsiPath)) {
    throw "MSI not found: $MsiPath"
}
$MsiPath = (Resolve-Path $MsiPath).Path
Write-Host "Patching MSI for launch-after-install: $MsiPath"

$installer = New-Object -ComObject WindowsInstaller.Installer
# 1 = msiOpenDatabaseModeTransact (read/write, changes committed at the end).
$database = $installer.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $installer, @($MsiPath, 1))

function Invoke-MsiView {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $false)][int]$FetchColumn = -1
    )
    $view = $database.GetType().InvokeMember("OpenView", "InvokeMethod", $null, $database, @($Sql))
    try {
        $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null)
        if ($FetchColumn -lt 1) { return $null }
        $record = $view.GetType().InvokeMember("Fetch", "InvokeMethod", $null, $view, $null)
        if ($null -eq $record) { return $null }
        return $record.GetType().InvokeMember("StringData", "GetProperty", $null, $record, @($FetchColumn))
    } finally {
        $view.GetType().InvokeMember("Close", "InvokeMethod", $null, $view, $null)
    }
}

$existingAction = Invoke-MsiView -Sql "SELECT ``Action`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'JpLaunchApp'" -FetchColumn 1
if ($existingAction -eq "JpLaunchApp") {
    Write-Host "Launch-after-install rows already present - nothing to do."
    return
}

# Type 51 = set property from formatted text. Type 178 = EXE (2) + property
# source (48) + async return (128).
Invoke-MsiView -Sql "INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('JpSetLaunchCmd', 51, 'JpLaunchCmd', '[%WINDIR]\explorer.exe')"
Invoke-MsiView -Sql "INSERT INTO ``CustomAction`` (``Action``, ``Type``, ``Source``, ``Target``) VALUES ('JpLaunchApp', 178, 'JpLaunchCmd', '`"[INSTALLDIR]Muzik.exe`"')"

# InstallFinalize is standard sequence 6600; run both actions right after it.
Invoke-MsiView -Sql "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('JpSetLaunchCmd', 'NOT Installed', 6601)"
Invoke-MsiView -Sql "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Condition``, ``Sequence``) VALUES ('JpLaunchApp', 'NOT Installed', 6602)"

$database.GetType().InvokeMember("Commit", "InvokeMethod", $null, $database, $null)

# Verify the committed row before declaring success. MSI StringData renders
# integer columns with padding and COM interop can wrap the value, so flatten
# the result and check the row by content instead of exact equality.
$sequence = (Invoke-MsiView -Sql "SELECT ``Sequence`` FROM ``InstallExecuteSequence`` WHERE ``Action`` = 'JpLaunchApp'" -FetchColumn 1 | Out-String)
if (-not $sequence.Contains("6602")) {
    throw "MSI patch verification failed: JpLaunchApp row missing after commit (got '$($sequence.Trim())')"
}
Write-Host "MSI patched successfully: Muzik launches after a fresh install (via explorer, non-elevated)."
