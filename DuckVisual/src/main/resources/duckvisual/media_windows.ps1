# Duck Visual - now playing bridge for Windows.
# Reads the current media session from Windows' Global System Media Transport Controls
# (the same source as the volume flyout: browsers, Spotify, Yandex Music, AIMP, ...)
# and prints one JSON line per update. Commands are read from a file written by the mod.
param([string]$CommandFile, [string]$ThumbDir)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Emit([string]$line) {
    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
}

try {
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, [Type]$type) {
    $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
    $task.Wait(-1) | Out-Null
    return $task.Result
}

[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime] | Out-Null
[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType=WindowsRuntime] | Out-Null

$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
    ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
    # e.g. Windows older than 10 1809, or WinRT unavailable
    Emit ('{"error":' + (ConvertTo-Json ([string]$_.Exception.Message)) + '}')
    Start-Sleep -Seconds 30
    exit 1
}

$lastKey = ''
$thumbIndex = 0
$thumbPath = ''

while ($true) {
    try {
        # commands from the mod: playpause / next / previous
        if ($CommandFile -and (Test-Path $CommandFile)) {
            $cmd = (Get-Content $CommandFile -Raw).Trim()
            Remove-Item $CommandFile -ErrorAction SilentlyContinue
            $s = $manager.GetCurrentSession()
            if ($s -ne $null) {
                switch ($cmd) {
                    'playpause' { Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null }
                    'next'      { Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null }
                    'previous'  { Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null }
                }
            }
        }

        $session = $manager.GetCurrentSession()
        if ($session -eq $null) {
            Emit '{"none":true}'
        } else {
            $props = Await ($session.TryGetMediaPropertiesAsync()) `
                ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $playback = $session.GetPlaybackInfo()
            $timeline = $session.GetTimelineProperties()

            $key = "$($session.SourceAppUserModelId)|$($props.Title)|$($props.Artist)"
            if ($key -ne $lastKey) {
                $lastKey = $key
                $thumbPath = ''
                if ($props.Thumbnail -ne $null -and $ThumbDir) {
                    try {
                        $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                        $netStream = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                        $thumbIndex++
                        $path = Join-Path $ThumbDir ("cover_" + ($thumbIndex % 4) + ".img")
                        $fs = [System.IO.File]::Create($path)
                        $netStream.CopyTo($fs)
                        $fs.Close()
                        $netStream.Dispose()
                        $thumbPath = $path
                    } catch { $thumbPath = '' }
                }
            }

            $obj = [ordered]@{
                title    = $props.Title
                artist   = $props.Artist
                album    = $props.AlbumTitle
                app      = $session.SourceAppUserModelId
                status   = "$($playback.PlaybackStatus)"
                position = $timeline.Position.TotalSeconds
                duration = ($timeline.EndTime - $timeline.StartTime).TotalSeconds
                updated  = $timeline.LastUpdatedTime.ToUnixTimeMilliseconds()
                now      = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
                thumb    = $thumbPath
            }
            Emit ($obj | ConvertTo-Json -Compress)
        }
    } catch {
        Emit ('{"error":' + (ConvertTo-Json ([string]$_.Exception.Message)) + '}')
    }
    Start-Sleep -Milliseconds 700
}
