using System;
using System.IO;
using System.Windows;

namespace PcClient;

public partial class App : System.Windows.Application
{
    private string ExeLogFilePath => Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "App.log");

    private string LocalAppDataLogFilePath
    {
        get
        {
            string text = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PcClient");
            Directory.CreateDirectory(text);
            return Path.Combine(text, "App.log");
        }
    }

    private void Log(string message)
    {
        try
        {
            string contents = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}{Environment.NewLine}";
            try { File.AppendAllText(ExeLogFilePath, contents); } catch { }
            try { File.AppendAllText(LocalAppDataLogFilePath, contents); } catch { }
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        Log("App.OnStartup entered");
        AppDomain.CurrentDomain.UnhandledException += (object _, UnhandledExceptionEventArgs args) =>
        {
            try { Log($"UNHANDLED: {args.ExceptionObject}"); } catch { }
        };
        AppDomain.CurrentDomain.ProcessExit += (object? _, EventArgs __) =>
        {
            try { Log("ProcessExit"); } catch { }
        };
        base.OnStartup(e);
        Log("App.OnStartup finished");
    }
}