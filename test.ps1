Add-Type -AssemblyName PresentationFramework
$xaml = '<TextBlock xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" Text="{Binding UsagePercent, StringFormat={}{0:F0}%}" />'
try {
    [System.Windows.Markup.XamlReader]::Parse($xaml) | Out-Null
    Write-Host "OK"
} catch {
    Write-Host "ERROR: $_"
}
