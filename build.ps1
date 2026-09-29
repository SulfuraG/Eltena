[CmdletBinding()]
param(
    [ValidateSet('All','EltenaCore','EltenaEffectCore','EltenaSoundCore','EltenaAddon','EltenaEffect','EltenaSound')]
    [string]$Module = 'All',
    [string]$GradleExecutable = 'gradle',
    [string]$MythicMobsJar
)
$ErrorActionPreference = 'Stop'
$modules = [ordered]@{
    EltenaCore = 'plugins/EltenaCore'
    EltenaEffectCore = 'plugins/EltenaEffectCore'
    EltenaSoundCore = 'plugins/EltenaSoundCore'
    EltenaAddon = 'mods/EltenaAddon'
    EltenaEffect = 'mods/EltenaEffect'
    EltenaSound = 'mods/EltenaSound'
}
$selected = if ($Module -eq 'All') { @($modules.Keys) } else { @($Module) }
if ('EltenaCore' -in $selected) {
    if (-not $MythicMobsJar -or -not (Test-Path -LiteralPath $MythicMobsJar -PathType Leaf)) {
        throw 'Specify a separately obtained MythicMobs 5.12.0 jar with -MythicMobsJar.'
    }
    $MythicMobsJar = (Resolve-Path -LiteralPath $MythicMobsJar).Path
}
$gradleVersion = & $GradleExecutable --version
if ($LASTEXITCODE -ne 0 -or ($gradleVersion -join "`n") -notmatch 'Gradle 9\.4\.1(?:\s|$)') {
    throw 'Gradle 9.4.1 is required.'
}
foreach ($name in $selected) {
    $projectPath = Join-Path $PSScriptRoot $modules[$name]
    $buildArgs = @('-p', $projectPath, 'build', '--no-daemon', '--console=plain')
    if ($name -eq 'EltenaCore') { $buildArgs += "-PmythicMobsJar=$MythicMobsJar" }
    & $GradleExecutable @buildArgs
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $name" }
}
