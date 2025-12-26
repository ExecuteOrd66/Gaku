$allOldFiles = git ls-tree -r cfeb55c --name-only | Select-String "app/src/main/java/ca/fuwafuwa/kaku/"

foreach ($oldPath in $allOldFiles) {
    $newPath = $oldPath.ToString().Replace("ca/fuwafuwa/kaku", "ca/fuwafuwa/gaku")
    $dir = [System.IO.Path]::GetDirectoryName($newPath)
    
    # Create the directory structure if missing
    if (!(Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force }
    
    # Copy the file content
    Write-Host "Rescuing: $newPath"
    git show "cfeb55c:$oldPath" > $newPath
    
    # Batch-fix the package and all internal imports from Kaku to Gaku
    (Get-Content $newPath) -replace 'ca.fuwafuwa.kaku', 'ca.fuwafuwa.gaku' | Set-Content $newPath
}