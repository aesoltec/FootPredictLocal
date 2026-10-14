# Diagnostic ADB — 2e Infinix invisible (XOS)
# Usage : powershell -ExecutionPolicy Bypass -File scripts/diagnose-infinix.ps1
# Ne modifie rien : diagnostique puis affiche la procédure manuelle restante.

$ErrorActionPreference = "SilentlyContinue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

Write-Host "=== 1. Appareils visibles ==="
& $adb devices -l

Write-Host ""
Write-Host "=== 2. Relance forcée du démon ==="
& $adb kill-server
Start-Sleep -Seconds 2
& $adb start-server
& $adb devices -l

Write-Host ""
Write-Host "=== 3. Périphériques USB (Infinix = Transsion) ==="
Get-PnpDevice -Class USB -Status OK | Select-Object FriendlyName, InstanceId |
    Where-Object { $_.FriendlyName -match 'ADB|Android|Transsion|Infinix|MTK|MediaTek' }

Write-Host ""
Write-Host "=== 4. Fastboot (au cas où le téléphone y serait) ==="
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\fastboot.exe" devices

Write-Host ""
Write-Host "=== PROCÉDURE MANUELLE XOS (si toujours absent) ==="
Write-Host "1. Câble DATA (pas charge seule) + autre port USB (éviter les hubs)."
Write-Host "2. Téléphone : Paramètres > À propos > taper 7x Numéro de build."
Write-Host "3. Options développeur : Débogage USB = ON."
Write-Host "4. Brancher > notification USB > choisir 'Transfert de fichiers (MTP)'."
Write-Host "   XOS exige souvent le mode MTP pour activer le pont ADB."
Write-Host "5. Valider l'empreinte RSA sur l'écran du téléphone (cocher Toujours)."
Write-Host "6. Si 'non autorisé' persiste : Options développeur >"
Write-Host "   'Révoquer les autorisations de débogage USB' > rebrancher."
Write-Host "7. Redémarrer le téléphone + relancer ce script."
Write-Host "8. Dernier recours : pilote USB Google (SDK Manager > Google USB Driver)"
Write-Host "   ou pilote Infinix/MTK officiel, puis autre PC/câble."
