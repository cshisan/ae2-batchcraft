param(
    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot)
)

Add-Type -AssemblyName System.Drawing

$sourcePath = Join-Path $RepositoryRoot 'docs/port_icons.png'
if (-not (Test-Path -LiteralPath $sourcePath)) {
    throw "Missing port icon source sheet: $sourcePath"
}

$cellSize = 418
$outputSize = 64
$maxIconSize = 20
$background = [System.Drawing.Color]::FromArgb(255, 38, 42, 47)
$icons = [ordered]@{
    energy = @{ column = 0; row = 0; x = 75; y = 25; width = 270; height = 310 }
    redstone = @{ column = 1; row = 0; x = 56; y = 60; width = 305; height = 263 }
    drop = @{ column = 2; row = 0; x = 60; y = 24; width = 290; height = 311 }
    place = @{ column = 0; row = 1; x = 50; y = 20; width = 310; height = 315 }
    break = @{ column = 1; row = 1; x = 37; y = 41; width = 325; height = 294 }
    transfer = @{ column = 2; row = 1; x = 63; y = 41; width = 305; height = 277 }
    return = @{ column = 0; row = 2; x = 64; y = 31; width = 284; height = 282 }
    extract = @{ column = 1; row = 2; x = 78; y = 29; width = 258; height = 284 }
    pickup = @{ column = 2; row = 2; x = 41; y = 18; width = 327; height = 315 }
}
$platforms = @('mc1.12.2-forge', 'mc1.16.5-forge', 'mc1.20.1-forge', 'mc1.21.1-neoforge')

$sheet = [System.Drawing.Bitmap]::new($sourcePath)
$imageAttributes = [System.Drawing.Imaging.ImageAttributes]::new()
try {
    if ($sheet.Width -ne $cellSize * 3 -or $sheet.Height -ne $cellSize * 3) {
        throw "Expected a 1254x1254 source sheet, got $($sheet.Width)x$($sheet.Height)."
    }

    $imageAttributes.SetColorKey(
            [System.Drawing.Color]::FromArgb(0, 0, 0),
            [System.Drawing.Color]::FromArgb(90, 90, 90),
            [System.Drawing.Imaging.ColorAdjustType]::Bitmap)

    foreach ($name in $icons.Keys) {
        $icon = $icons[$name]
        $scale = [Math]::Min($maxIconSize / $icon.width, $maxIconSize / $icon.height)
        $targetWidth = [Math]::Max(1, [Math]::Round($icon.width * $scale))
        $targetHeight = [Math]::Max(1, [Math]::Round($icon.height * $scale))
        $targetX = [Math]::Round(($outputSize - $targetWidth) / 2)
        $targetY = [Math]::Round(($outputSize - $targetHeight) / 2)
        $sourceX = $icon.column * $cellSize + $icon.x
        $sourceY = $icon.row * $cellSize + $icon.y

        $output = [System.Drawing.Bitmap]::new(
                $outputSize,
                $outputSize,
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($output)
            try {
                $graphics.Clear($background)
                $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceOver
                $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::AssumeLinear
                $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
                $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
                $graphics.DrawImage(
                        $sheet,
                        [System.Drawing.Rectangle]::new($targetX, $targetY, $targetWidth, $targetHeight),
                        $sourceX,
                        $sourceY,
                        $icon.width,
                        $icon.height,
                        [System.Drawing.GraphicsUnit]::Pixel,
                        $imageAttributes)
            }
            finally {
                $graphics.Dispose()
            }

            foreach ($platform in $platforms) {
                $directory = Join-Path $RepositoryRoot `
                        "platforms/$platform/src/main/resources/assets/ae2_batchcraft/textures/part/p2p"
                New-Item -ItemType Directory -Force -Path $directory | Out-Null
                $output.Save(
                        (Join-Path $directory "unit_port_$name.png"),
                        [System.Drawing.Imaging.ImageFormat]::Png)
            }
        }
        finally {
            $output.Dispose()
        }
    }
}
finally {
    $imageAttributes.Dispose()
    $sheet.Dispose()
}

Write-Host "Generated $($icons.Count) centered 64x64 port icons from docs/port_icons.png."
