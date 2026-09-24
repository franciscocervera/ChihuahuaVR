$ErrorActionPreference = "Stop"

$expected = @(
    "narr_dest_barrancas_cobre.mp3",
    "narr_dest_chepe.mp3",
    "narr_dest_centro_chihuahua.mp3",
    "narr_dest_paquime.mp3",
    "narr_dest_samalayuca.mp3",
    "narr_dest_creel_arareko.mp3",
    "narr_dest_basaseachi.mp3",
    "narr_dest_parral.mp3",
    "narr_dest_batopilas.mp3",
    "narr_dest_sinforosa.mp3"
)

$rawPath = Join-Path $PSScriptRoot "..\app\src\main\res\raw"
$missing = $expected | Where-Object { -not (Test-Path (Join-Path $rawPath $_)) }

if ($missing.Count -gt 0) {
    Write-Error ("Faltan narraciones: " + ($missing -join ", "))
}

Write-Host "Catálogo de narraciones de destinos completo ($($expected.Count) archivos)."
