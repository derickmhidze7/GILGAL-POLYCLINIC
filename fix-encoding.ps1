# Fix mojibake (garbled UTF-8) characters in all HTML templates
# 
# Root cause: Unicode chars were encoded as UTF-8, then mis-read as Windows-1252,
# then re-saved as UTF-8, producing multi-byte mojibake sequences.
#
# Mapping (each mojibake char = 3 Unicode codepoints replacing 1 original char):
#   â€"  U+00E2 + U+20AC + U+201D  => originally — (em dash U+2014)
#   â€¦  U+00E2 + U+20AC + U+00A6  => originally … (ellipsis U+2026)
#   â‰¥  U+00E2 + U+2030 + U+00A5  => originally ≥ (U+2265)
#   â"€  U+00E2 + U+201D + U+20AC  => originally ─ (box drawing horiz U+2500)
#   â•  U+00E2 + U+2022 + U+2019  => originally ═ (box drawing double horiz U+2550)

$dir = "C:\Users\Derick Mhidze\ADAGS-HOSPITAL\hospital\src\main\resources\templates"

# Bad (mojibake) strings — built from confirmed Unicode codepoints
$badEmDash   = "$([char]0x00E2)$([char]0x20AC)$([char]0x201D)"  # â€"  -> em dash
$badEllipsis = "$([char]0x00E2)$([char]0x20AC)$([char]0x00A6)"  # â€¦  -> ...
$badGeq      = "$([char]0x00E2)$([char]0x2030)$([char]0x00A5)"  # â‰¥  -> >=
$badBox      = "$([char]0x00E2)$([char]0x201D)$([char]0x20AC)"  # â"€  -> -  (box horiz)
$badBoxDbl   = "$([char]0x00E2)$([char]0x2022)$([char]0x2019)"  # â•   -> =  (box double)

# Good replacements
$goodEmDash   = "$([char]0x2014)"  # — proper em dash U+2014
$goodEllipsis = "..."
$goodGeq      = ">="
$goodBox      = "-"
$goodBoxDbl   = "="

$enc = [System.Text.Encoding]::UTF8

$files = Get-ChildItem -Path $dir -Filter "*.html" -Recurse
$totalChanged = 0

foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, $enc)
    $original = $content

    $content = $content.Replace($badEmDash,   $goodEmDash)
    $content = $content.Replace($badEllipsis, $goodEllipsis)
    $content = $content.Replace($badGeq,      $goodGeq)
    $content = $content.Replace($badBox,      $goodBox)
    $content = $content.Replace($badBoxDbl,   $goodBoxDbl)

    if ($content -ne $original) {
        # Write back without BOM
        $bytes = $enc.GetBytes($content)
        [System.IO.File]::WriteAllBytes($file.FullName, $bytes)
        $totalChanged++
        Write-Host "Fixed: $($file.Name)"
    }
}

Write-Host ""
Write-Host "Done. Files updated: $totalChanged"
