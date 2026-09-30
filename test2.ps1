Add-Type -AssemblyName PresentationFramework
$xaml = '<RepeatButton xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" Command="Slider.DecreaseLarge" />'
try {
    [System.Windows.Markup.XamlReader]::Parse($xaml) | Out-Null
    Write-Host "OK"
} catch {
    Write-Host "ERROR: $_"
}
