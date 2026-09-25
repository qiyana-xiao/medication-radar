# 用药雷达图标生成脚本：GDI+ 绘制多尺寸 PNG 并合成 ICO
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$outDir = "d:\projiect1\medication-radar\assets"
$sizes = @(256, 128, 64, 48, 32, 16)

function New-RoundRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc(($x + $w - $d), $y, $d, $d, 270, 90)
    $p.AddArc(($x + $w - $d), ($y + $h - $d), $d, $d, 0, 90)
    $p.AddArc($x, ($y + $h - $d), $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function New-Pen([System.Drawing.Color]$color, [float]$width) {
    return New-Object System.Drawing.Pen -ArgumentList $color, $width
}

function New-LogoBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $s = $size / 256.0
    $simple = $size -le 48
    $blue = [System.Drawing.Color]::FromArgb(21, 112, 239)
    $blueDark = [System.Drawing.Color]::FromArgb(15, 95, 206)
    $blueLight = [System.Drawing.Color]::FromArgb(43, 143, 255)

    # 1. 圆角方形底板（渐变白→淡蓝）
    $bgPath = New-RoundRectPath (4 * $s) (4 * $s) (248 * $s) (248 * $s) (58 * $s)
    $p0 = New-Object System.Drawing.Point -ArgumentList 0, 0
    $p1 = New-Object System.Drawing.Point -ArgumentList $size, $size
    $bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush -ArgumentList $p0, $p1, ([System.Drawing.Color]::White), ([System.Drawing.Color]::FromArgb(220, 236, 251))
    $g.FillPath($bgBrush, $bgPath)
    $bgPen = New-Pen ([System.Drawing.Color]::FromArgb(186, 210, 245)) ([Math]::Max(1.0, 2 * $s))
    $g.DrawPath($bgPen, $bgPath)

    # 2. 雷达同心圆环
    $cx = 128 * $s; $cy = 128 * $s
    $r1 = 100 * $s
    $ringPen1 = New-Pen ([System.Drawing.Color]::FromArgb(235, 21, 112, 239)) ([Math]::Max(1.2, 7 * $s))
    $g.DrawEllipse($ringPen1, ($cx - $r1), ($cy - $r1), ($r1 * 2), ($r1 * 2))
    if (-not $simple) {
        $r2 = 76 * $s
        $ringPen2 = New-Pen ([System.Drawing.Color]::FromArgb(140, 21, 112, 239)) ([Math]::Max(1.0, 4.5 * $s))
        $g.DrawEllipse($ringPen2, ($cx - $r2), ($cy - $r2), ($r2 * 2), ($r2 * 2))
        $r3 = 54 * $s
        $ringPen3 = New-Pen ([System.Drawing.Color]::FromArgb(71, 21, 112, 239)) ([Math]::Max(1.0, 2.5 * $s))
        $g.DrawEllipse($ringPen3, ($cx - $r3), ($cy - $r3), ($r3 * 2), ($r3 * 2))

        # 3. 雷达扫描线（虚线）
        $dashPen = New-Pen ([System.Drawing.Color]::FromArgb(204, 21, 112, 239)) ([Math]::Max(1.0, 4 * $s))
        $dashPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
        $dashPen.DashCap = [System.Drawing.Drawing2D.DashCap]::Round
        $dashPen.DashPattern = @(3.5, 2.0)
        $g.DrawLine($dashPen, $cx, $cy, (203 * $s), (63 * $s))
    }

    # 4. 中心医疗十字（两条圆角矩形，同渐变填充）
    $cp0 = New-Object System.Drawing.Point -ArgumentList ([int](73 * $s)), ([int](81 * $s))
    $cp1 = New-Object System.Drawing.Point -ArgumentList ([int](183 * $s)), ([int](175 * $s))
    $crossBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush -ArgumentList $cp0, $cp1, $blueLight, $blueDark
    $corner = 8 * $s
    if ($simple) { $corner = 10 * $s }
    $vPath = New-RoundRectPath (113 * $s) (81 * $s) (30 * $s) (94 * $s) $corner
    $hPath = New-RoundRectPath (81 * $s) (113 * $s) (94 * $s) (30 * $s) $corner
    $g.FillPath($crossBrush, $vPath)
    $g.FillPath($crossBrush, $hPath)

    if (-not $simple) {
        # 5. 红色信号点 + 白色高光（右上角）
        $redR = 15 * $s
        $redBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(239, 68, 68))
        $g.FillEllipse($redBrush, ((203 - 15) * $s), ((58 - 15) * $s), ($redR * 2), ($redR * 2))
        $hlBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(230, 255, 255, 255))
        $g.FillEllipse($hlBrush, ((198 - 5) * $s), ((52 - 5) * $s), (10 * $s), (10 * $s))

        # 6. 药丸元素（左下角，斜置胶囊）
        $state = $g.Save()
        $g.TranslateTransform(62 * $s, 190 * $s)
        $g.RotateTransform(-38)
        $g.TranslateTransform(-62 * $s, -190 * $s)
        $pillPath = New-RoundRectPath (40 * $s) (181 * $s) (44 * $s) (18 * $s) (9 * $s)
        $pillBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(77, 154, 255))
        $pillPen = New-Pen ([System.Drawing.Color]::FromArgb(43, 143, 255)) ([Math]::Max(1.0, 2 * $s))
        $g.FillPath($pillBrush, $pillPath)
        $g.DrawPath($pillPen, $pillPath)
        $g.DrawLine($pillPen, (62 * $s), (181 * $s), (62 * $s), (199 * $s))
        $g.Restore($state)

        # 7. 小信号点（黄色）
        $dotBrush = New-Object System.Drawing.SolidBrush -ArgumentList ([System.Drawing.Color]::FromArgb(255, 213, 79))
        $g.FillEllipse($dotBrush, ((176 - 6) * $s), ((172 - 6) * $s), (12 * $s), (12 * $s))
    }

    $g.Dispose()
    return $bmp
}

# 生成各尺寸 PNG
$pngBytes = @{}
foreach ($sz in $sizes) {
    $bmp = New-LogoBitmap $sz
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngBytes[$sz] = $ms.ToArray()
    $ms.Close()
    if ($sz -eq 256) {
        $bmp.Save("$outDir\logo-256.png", [System.Drawing.Imaging.ImageFormat]::Png)
    }
    $bmp.Dispose()
    Write-Host "PNG ${sz}x${sz} OK ($($pngBytes[$sz].Length) bytes)"
}

# 合成 ICO（PNG 内嵌格式，Vista+）
$ico = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter($ico)
$bw.Write([uint16]0); $bw.Write([uint16]1); $bw.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $sz = $sizes[$i]
    $b = if ($sz -ge 256) { [byte]0 } else { [byte]$sz }
    $bw.Write($b); $bw.Write($b); $bw.Write([byte]0); $bw.Write([byte]0)
    $bw.Write([uint16]1); $bw.Write([uint16]32)
    $bw.Write([uint32]$pngBytes[$sz].Length)
    $bw.Write([uint32]$offset)
    $offset += $pngBytes[$sz].Length
}
foreach ($sz in $sizes) { $bw.Write($pngBytes[$sz]) }
$bw.Flush()
[System.IO.File]::WriteAllBytes("$outDir\medication-radar.ico", $ico.ToArray())
$bw.Close()

$icoInfo = Get-Item "$outDir\medication-radar.ico"
Write-Host ""
Write-Host "ICO OK: $($icoInfo.FullName) ($([Math]::Round($icoInfo.Length/1KB, 1)) KB, $($sizes.Count) sizes)"
