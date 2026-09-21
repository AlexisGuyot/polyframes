# Checks that the tools PolyFrames needs are installed on Windows, and that
# the machine is configured so that compilation timings are meaningful.
# Run from PowerShell:  .\scripts\check-env.ps1

$ErrorActionPreference = "Continue"
$status = 0

function Report($name, $probe) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        "{0,-9} MISSING" -f $name
        $script:status = 1
        return
    }
    $v = (& $probe 2>&1 | Select-Object -First 1)
    "{0,-9} ok       {1}" -f $name, $v
}

"== tools =="
Report "java"  { java -version }
Report "javac" { javac -version }
Report "sbt"   { sbt --script-version }
Report "git"   { git --version }
Report "curl"  { curl.exe --version }

""
"== hadoop native binaries (required by Spark on Windows) =="
$hh = $env:HADOOP_HOME
if ([string]::IsNullOrEmpty($hh)) {
    "HADOOP_HOME  NOT SET"
    $status = 1
} else {
    "HADOOP_HOME  $hh"
    foreach ($f in @("winutils.exe", "hadoop.dll")) {
        $p = Join-Path (Join-Path $hh "bin") $f
        if (Test-Path $p) { "  $f      found" }
        else { "  $f      MISSING at $p"; $status = 1 }
    }
}

""
"== platform =="
$cs = Get-CimInstance Win32_ComputerSystem
$os = Get-CimInstance Win32_OperatingSystem
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
"cpu       $($cpu.Name)"
"cores     $($cpu.NumberOfCores) physical / $($cpu.NumberOfLogicalProcessors) logical"
"ram       {0:N1} GiB" -f ($cs.TotalPhysicalMemory / 1GB)
"os        $($os.Caption) build $($os.BuildNumber)"
"workdir   $(Get-Location)"
if ("$(Get-Location)".Contains(" ")) {
    "          ^ this path contains a space. Spark does not handle those well."
    "            Move the project somewhere like C:\dev\polyframes."
    $status = 1
}

""
"== antivirus =="
try {
    $ex = (Get-MpPreference).ExclusionPath
    if ($null -eq $ex -or $ex.Count -eq 0) {
        "Defender exclusions: none"
        "          Real-time scanning of the build caches inflates and"
        "          destabilises compilation times, which section 5 measures."
        "          See README.md, section Environment."
    } else {
        "Defender exclusions:"
        $ex | ForEach-Object { "  $_" }
    }
} catch {
    "Defender status could not be read (not an error)."
}

""
if ($status -ne 0) {
    "Not ready. See README.md, section Environment."
    exit 1
}
"Ready. Next: sbt core/Test/compile"
