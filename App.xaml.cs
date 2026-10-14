using System;
using System.IO;
using System.Threading;
using System.Windows;

namespace PcClient;

public partial class App : System.Windows.Application
{
    private static Mutex? _singleInstanceMutex;

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
        bool createdNew;
        try
        {
            _singleInstanceMutex = new Mutex(true, "Global\\PCMasterControlCenter_SingleInstance_Mutex_2026", out createdNew);
        }
        catch
        {
            createdNew = true;
        }

        if (!createdNew)
        {
            Log("Another instance of PC Master Control is already running. Exiting.");
            System.Windows.MessageBox.Show("PC Master Control Center is already running. Please check your system tray (near the taskbar clock).", "Already Running", System.Windows.MessageBoxButton.OK, System.Windows.MessageBoxImage.Information);
            Shutdown();
            return;
        }

        AppDomain.CurrentDomain.UnhandledException += (object _, UnhandledExceptionEventArgs args) =>
        {
            try { Log($"UNHANDLED: {args.ExceptionObject}"); } catch { }
        };
        AppDomain.CurrentDomain.ProcessExit += (object? _, EventArgs __) =>
        {
            try { Log("ProcessExit"); } catch { }
            try { _singleInstanceMutex?.ReleaseMutex(); } catch { }
        };
        base.OnStartup(e);
        Log("App.OnStartup finished");
    }
}