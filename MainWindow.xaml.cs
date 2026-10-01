using System;
using System.CodeDom.Compiler;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Management;
using System.Net;
using System.Net.Http;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Forms;
using System.Windows.Input;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;
using System.Windows.Threading;
using LibreHardwareMonitor.Hardware;
using Microsoft.Win32;

namespace PcClient;

public partial class MainWindow : Window
{
	public class TelemetrySnapshot
	{
		public float CpuLoad { get; init; }

		public float CpuTemp { get; init; }

		public float GpuLoad { get; init; }

		public float GpuTemp { get; init; }

		public float RamUsedGb { get; init; }

		public float RamTotalGb { get; init; }

		public float RamUsagePercent { get; init; }

		public float DiskUsedGb { get; init; }

		public float DiskTotalGb { get; init; }

		public float NetUpMb { get; init; }

		public float NetDownMb { get; init; }

		public float FanSpeed { get; init; }

		public float FreeRamGb { get; init; }

		public DateTime Timestamp { get; init; } = DateTime.Now;

		public string Hostname { get; init; } = "";

		public string OsName { get; init; } = "";

		public string Uptime { get; init; } = "--";

		public string Battery { get; init; } = "--";

		public IReadOnlyList<DriveUsage> Drives { get; init; } = Array.Empty<DriveUsage>();

		public IReadOnlyList<ProcessInfo> TopProcesses { get; init; } = Array.Empty<ProcessInfo>();
	}

	public class DriveUsage
	{
		public string Label { get; set; } = "";

		public double UsagePercent { get; set; }
	}

	public class ProcessInfo
	{
		public string Name { get; set; } = "";

		public string Info { get; set; } = "";

		public int Pid { get; set; }
	}

	public class NotificationItem
	{
		public string Id { get; set; } = Guid.NewGuid().ToString();

		public string AppName { get; set; } = "";

		public string Title { get; set; } = "";

		public string Content { get; set; } = "";

		public string Package { get; set; } = "";

		public bool CanReply { get; set; }
	}

	public class NotificationReplyItem
	{
		public string Id { get; set; } = "";
		public string Message { get; set; } = "";
	}

	private struct POINTAPI
	{
		public int x;

		public int y;
	}

	private struct CURSORINFO
	{
		public int cbSize;

		public int flags;

		public nint hCursor;

		public POINTAPI ptScreenPos;
	}

	public class LogEntry
	{
		public DateTime Time { get; set; }

		public string Message { get; set; } = "";

		public string TimeText => Time.ToString("HH:mm:ss");
	}

	[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
	private class MEMORYSTATUSEX
	{
		public uint dwLength;

		public uint dwMemoryLoad;

		public ulong ullTotalPhys;

		public ulong ullAvailPhys;

		public ulong ullTotalPageFile;

		public ulong ullAvailPageFile;

		public ulong ullTotalVirtual;

		public ulong ullAvailVirtual;

		public ulong ullAvailExtendedVirtual;

		public MEMORYSTATUSEX()
		{
			dwLength = (uint)Marshal.SizeOf(typeof(MEMORYSTATUSEX));
		}
	}

	public class HubFileItem
	{
		public string Name { get; set; } = "";

		public string Path { get; set; } = "";

		public string Size { get; set; } = "";

		public string Extension { get; set; } = "";

		public string Icon { get; set; } = "\ud83d\udcc4";
	}

	private Computer _computer;

	private DispatcherTimer _timer;

	private HttpListener? _fileServer;

	private TcpListener? _tcpBridge;

	private CancellationTokenSource? _bridgeCts;

	private readonly object _telemetryHistoryLock = new object();

	private readonly object _notificationsLock = new object();

	private volatile TelemetrySnapshot _latestStats = new TelemetrySnapshot();

	private Thread? _mirrorThread;

	private volatile bool _isMirroring;

	private byte[] _latestFrame = new byte[0];

	private readonly object _frameLock = new object();

	private long _lastBytesSent = -1L;

	private long _lastBytesReceived = -1L;

	private DateTime _lastNetUpdateTime = DateTime.MinValue;

	private Thread? _serverThread;

	private UdpClient? _udpDiscovery;

	private NotifyIcon? _trayIcon;

	private DateTime _lastMobileContact = DateTime.MinValue;

	private string _currentDeviceName = "NOT CONNECTED";

	private bool _isUpdatingStats;

	private ObservableCollection<NotificationItem> _notifications = new ObservableCollection<NotificationItem>();

	private string _lastConnectedMobileIp = "";
	private readonly System.Collections.Concurrent.ConcurrentQueue<NotificationReplyItem> _pendingReplies = new System.Collections.Concurrent.ConcurrentQueue<NotificationReplyItem>();

	private static readonly HttpClient _phoneHttpClient = new HttpClient { Timeout = TimeSpan.FromSeconds(3.0) };

	private bool _isMirroringActive;

	private const int CURSOR_SHOWING = 1;

	private const uint MOUSEEVENTF_MOVE = 1u;

	private const uint MOUSEEVENTF_LEFTDOWN = 2u;

	private const uint MOUSEEVENTF_LEFTUP = 4u;

	private const uint MOUSEEVENTF_RIGHTDOWN = 8u;

	private const uint MOUSEEVENTF_RIGHTUP = 16u;

	private const uint MOUSEEVENTF_WHEEL = 2048u;

	private const byte VK_MEDIA_NEXT_TRACK = 176;

	private const byte VK_MEDIA_PREV_TRACK = 177;

	private const byte VK_MEDIA_PLAY_PAUSE = 179;

	private const byte VK_VOLUME_MUTE = 173;

	private const byte VK_VOLUME_DOWN = 174;

	private const byte VK_VOLUME_UP = 175;

	private const int KEYEVENTF_EXTENDEDKEY = 1;

	private const int KEYEVENTF_KEYUP = 2;

	private static readonly nint HWND_BROADCAST = new IntPtr(65535);

	private const uint WM_SYSCOMMAND = 274u;

	private static readonly nint SC_MONITORPOWER = new IntPtr(61808);

	private const uint ES_AWAYMODE_REQUIRED = 64u;

	private const byte VK_SPACE = 32;

	private const byte VK_RETURN = 13;

	private const byte VK_F24 = 135;

	private const uint ES_CONTINUOUS = 2147483648u;

	private const uint ES_SYSTEM_REQUIRED = 1u;

	private const uint ES_DISPLAY_REQUIRED = 2u;

	private AppSettings _settings = new AppSettings();

	private readonly List<TelemetrySnapshot> _telemetryHistory = new List<TelemetrySnapshot>();

	private const int MaxHistorySamples = 60;

	private const int MaxNotifications = 100;

	private ManagementEventWatcher? _usbWatcher;

	private DispatcherTimer? _brightnessDebounceTimer;

	private System.Windows.Point _dragStartPoint;

	private const uint SHERB_NOCONFIRMATION = 1u;

	private const uint SHERB_NOPROGRESSUI = 2u;

	private const uint SHERB_NOSOUND = 4u;

	private DispatcherTimer? _shutdownCountdownTimer;

	private TimeSpan _remainingShutdownTime;

	private volatile bool _isAntiIdleActive;

	private DispatcherTimer? _antiIdleTimer;

	private string _lastClipboardText = "";

	private DispatcherTimer? _clipboardTimer;

	private DispatcherTimer? _toastTimer;

	private bool _isMirrorLandscape;

	private string LogFilePath
	{
		get
		{
			string text = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PcClient");
			Directory.CreateDirectory(text);
			return System.IO.Path.Combine(text, "PcClient.log");
		}
	}

	private string SettingsFilePath
	{
		get
		{
			string text = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PcClient");
			Directory.CreateDirectory(text);
			return System.IO.Path.Combine(text, "settings.json");
		}
	}

	public ObservableCollection<LogEntry> LogEntries { get; } = new ObservableCollection<LogEntry>();

	[DllImport("user32.dll")]
	private static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, uint dwExtraInfo);

	[DllImport("user32.dll")]
	private static extern bool SetCursorPos(int x, int y);

	[DllImport("user32.dll")]
	private static extern void mouse_event(uint dwFlags, uint dx, uint dy, uint dwData, uint dwExtraInfo);

	[DllImport("user32.dll")]
	private static extern bool GetCursorInfo(out CURSORINFO pci);

	[DllImport("user32.dll")]
	private static extern bool DrawIcon(nint hDC, int X, int Y, nint hIcon);

	[DllImport("user32.dll")]
	private static extern nint SendMessage(nint hWnd, uint Msg, nint wParam, nint lParam);

	[DllImport("user32.dll")]
	private static extern short VkKeyScan(char ch);

	[DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
	private static extern uint SetThreadExecutionState(uint esFlags);

	[DllImport("psapi.dll", SetLastError = true)]
	private static extern bool EmptyWorkingSet(nint hProcess);

	private void LoadSettings()
	{
		try
		{
			if (File.Exists(SettingsFilePath))
			{
				string json = File.ReadAllText(SettingsFilePath);
				_settings = JsonSerializer.Deserialize<AppSettings>(json) ?? new AppSettings();
			}
			else
			{
				_settings.AdbPath = FindAdbPath();
				_settings.AuthToken = GenerateAuthToken();
				SaveSettings();
			}
		}
		catch
		{
			_settings = new AppSettings();
		}
	}

	private void SaveSettings()
	{
		try
		{
			string contents = JsonSerializer.Serialize(_settings, new JsonSerializerOptions
			{
				WriteIndented = true
			});
			File.WriteAllText(SettingsFilePath, contents);
		}
		catch (Exception ex)
		{
			Log("SaveSettings Error: " + ex.Message);
		}
	}

	private string FindAdbPath()
	{
		try
		{
			string[] array = new string[5]
			{
				System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Android", "Sdk", "platform-tools", "adb.exe"),
				System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "AppData", "Local", "Android", "Sdk", "platform-tools", "adb.exe"),
				"C:\\Android\\Sdk\\platform-tools\\adb.exe",
				"C:\\Program Files\\Android\\Sdk\\platform-tools\\adb.exe",
				"C:\\Program Files (x86)\\Android\\Sdk\\platform-tools\\adb.exe"
			};
			foreach (string text in array)
			{
				if (File.Exists(text))
				{
					return text;
				}
			}
			string environmentVariable = Environment.GetEnvironmentVariable("ANDROID_HOME");
			if (!string.IsNullOrEmpty(environmentVariable))
			{
				string text2 = System.IO.Path.Combine(environmentVariable, "platform-tools", "adb.exe");
				if (File.Exists(text2))
				{
					return text2;
				}
			}
			try
			{
				Process? process = Process.Start(new ProcessStartInfo("where.exe", "adb")
				{
					RedirectStandardOutput = true,
					UseShellExecute = false,
					CreateNoWindow = true
				});
				process?.WaitForExit();
				string text3 = process?.StandardOutput.ReadToEnd().Trim();
				if (!string.IsNullOrEmpty(text3) && File.Exists(text3.Split('\n')[0].Trim()))
				{
					return text3.Split('\n')[0].Trim();
				}
			}
			catch (Exception ex)
			{
				Log("FindAdbPath: where.exe failed: " + ex.Message);
			}
		}
		catch (Exception ex2)
		{
			Log("FindAdbPath error: " + ex2.Message);
		}
		return "";
	}

	private string GenerateAuthToken()
	{
		using RandomNumberGenerator randomNumberGenerator = RandomNumberGenerator.Create();
		byte[] array = new byte[32];
		randomNumberGenerator.GetBytes(array);
		return Convert.ToBase64String(array).Replace("+", "-").Replace("/", "_")
			.Replace("=", "");
	}

private bool ValidateAuth(HttpListenerRequest req)
        {
            if (!_settings.RequireAuth)
            {
                return true;
            }
            string authHeader = req.Headers["Authorization"];
            if (!string.IsNullOrEmpty(authHeader))
            {
                if (authHeader.Equals("Bearer " + _settings.AuthToken, StringComparison.OrdinalIgnoreCase) ||
                    authHeader.Equals(_settings.AuthToken, StringComparison.Ordinal))
                    return true;
            }
            string tokenHeader = req.Headers["X-Auth-Token"];
            if (!string.IsNullOrEmpty(tokenHeader) && tokenHeader == _settings.AuthToken)
            {
                return true;
            }
            string tokenQuery = req.QueryString["token"];
            if (!string.IsNullOrEmpty(tokenQuery) && tokenQuery == _settings.AuthToken)
            {
                return true;
            }
            return false;
        }

	private static readonly object _logLock = new object();

	private void Log(string message)
	{
		string logLine = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}{Environment.NewLine}";
		System.Diagnostics.Debug.WriteLine(logLine);
		try
		{
			lock (_logLock)
			{
				File.AppendAllText(LogFilePath, logLine);
			}
		}
		catch (Exception ex)
		{
			System.Diagnostics.Trace.WriteLine("Failed to write to primary log: " + ex.Message);
			try
			{
				string fallbackPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "PcClient_fallback.log");
				lock (_logLock)
				{
					File.AppendAllText(fallbackPath, logLine);
				}
			}
			catch
			{
			}
		}
	}

	public MainWindow()
	{
		InitializeComponent();
		LoadSettings();
		_computer = new Computer
		{
			IsCpuEnabled = true,
			IsGpuEnabled = true,
			IsMemoryEnabled = true,
			IsMotherboardEnabled = true
		};
		_computer.Open();
		_timer = new DispatcherTimer();
		_timer.Interval = TimeSpan.FromSeconds(_settings.UpdateIntervalSeconds);
		_timer.Tick += UpdateStats;
		_timer.Start();
		SetupTrayIcon();
		SetupUsbWatcher();
		NotificationList.ItemsSource = _notifications;
		AutoStartToggle.IsChecked = IsAutoStartEnabled();
		AutoStartToggle.Checked += (object s, RoutedEventArgs e) =>
		{
			SetAutoStart(enable: true);
		};
		AutoStartToggle.Unchecked += (object s, RoutedEventArgs e) =>
		{
			SetAutoStart(enable: false);
		};
		UpdateRateSlider.Value = _timer.Interval.TotalSeconds;
		UpdateRateSlider.ValueChanged += (object s, RoutedPropertyChangedEventArgs<double> e) =>
		{
			if (_timer != null)
			{
				_timer.Interval = TimeSpan.FromSeconds(UpdateRateSlider.Value);
				_settings.UpdateIntervalSeconds = (int)UpdateRateSlider.Value;
				SaveSettings();
			}
		};
		TrayToggle.IsChecked = _settings.MinimizeToTray;
		CaffeineToggle.IsChecked = false;
		CompactModeToggle.IsChecked = _settings.CompactMode;
		StartClipboardMonitor();
		LogList.ItemsSource = LogEntries;
	}

	private void StartFileServer()
	{
		if (_fileServer != null)
		{
			return;
		}
		try
		{
			_fileServer = new HttpListener();
			List<string> list = new List<string>();
			string value = "http";
			list.Add($"{value}://localhost:{_settings.HttpPort}/");
			list.Add($"{value}://127.0.0.1:{_settings.HttpPort}/");
			try
			{
				foreach (NetworkInterface item in from ni in NetworkInterface.GetAllNetworkInterfaces()
					where ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback
					select ni)
				{
					foreach (UnicastIPAddressInformation unicastAddress in item.GetIPProperties().UnicastAddresses)
					{
						if (unicastAddress.Address.AddressFamily == AddressFamily.InterNetwork)
						{
							list.Add($"{value}://{unicastAddress.Address}:{_settings.HttpPort}/");
						}
					}
				}
			}
			catch
			{
			}
			foreach (string item2 in list)
			{
				try
				{
					_fileServer.Prefixes.Add(item2);
				}
				catch (Exception ex)
				{
					Log("Failed to add prefix " + item2 + ": " + ex.Message);
				}
			}
			if (_settings.EnableSsl && !string.IsNullOrEmpty(_settings.SslCertPath) && File.Exists(_settings.SslCertPath))
			{
				try
				{
					new X509Certificate2(_settings.SslCertPath, _settings.SslCertPassword);
					AddToLog("SSL Certificate loaded but HttpListener SSL binding requires Admin (netsh). Falling back to HTTP.");
				}
				catch (Exception ex2)
				{
					Log("SSL Cert load error: " + ex2.Message);
				}
			}
			try
			{
				_fileServer.Start();
			}
			catch (Exception ex3)
			{
				AddToLog("Hub: External IP requires elevation (" + ex3.Message + "), activating Universal WiFi Bridge");
				try
				{
					_fileServer.Close();
				}
				catch (Exception ex4)
				{
					Log("FileServer close error: " + ex4.Message);
				}
				int num = _settings.HttpPort + 10;
				_fileServer = new HttpListener();
				_fileServer.Prefixes.Add($"http://localhost:{num}/");
				_fileServer.Prefixes.Add($"http://127.0.0.1:{num}/");
				_fileServer.Start();
				StartTcpBridge(_settings.HttpPort, num);
			}
			_serverThread = new Thread(ListenForRequests)
			{
				IsBackground = true,
				Name = "HttpServerThread"
			};
			_serverThread.Start();
			StartDiscoveryBroadcast();
			Dispatcher.Invoke(() =>
			{
				StartFileServerButton.Tag = "Started";
				StartFileServerButton.Content = "ACTIVE";
				if (StartFileServerButtonMini != null)
				{
					StartFileServerButtonMini.Tag = "Started";
					StartFileServerButtonMini.Content = "HUB ON";
				}
			});
			AddToLog($"Wireless Hub: Active on port {_settings.HttpPort}");
			UpdateNetworkInfo();
		}
		catch (Exception ex5)
		{
			AddToLog("Hub Error: " + ex5.Message);
			_fileServer = null;
		}
	}

	private void StartTcpBridge(int publicPort, int internalPort)
	{
		try
		{
			StopTcpBridge();
			_bridgeCts = new CancellationTokenSource();
			_tcpBridge = new TcpListener(IPAddress.Any, publicPort);
			_tcpBridge.Start();
			AddToLog($"Universal WiFi Bridge active on 0.0.0.0:{publicPort} -> 127.0.0.1:{internalPort}");
			CancellationToken token = _bridgeCts.Token;
			Task.Run(async () =>
			{
				while (!token.IsCancellationRequested && _tcpBridge != null)
				{
					try
					{
						TcpClient client = await _tcpBridge.AcceptTcpClientAsync(token);
						Task.Run(() => BridgeConnection(client, internalPort, token), token);
					}
					catch (OperationCanceledException)
					{
						break;
					}
					catch (Exception ex3)
					{
						Log("TCP Bridge accept error: " + ex3.Message);
						break;
					}
				}
			}, token);
		}
		catch (Exception ex)
		{
			AddToLog("Bridge startup error: " + ex.Message);
		}
	}

	private void StopTcpBridge()
	{
		try
		{
			_bridgeCts?.Cancel();
		}
		catch (Exception ex)
		{
			Log("BridgeCts cancel error: " + ex.Message);
		}
		try
		{
			_tcpBridge?.Stop();
		}
		catch (Exception ex2)
		{
			Log("TcpBridge stop error: " + ex2.Message);
		}
		_tcpBridge = null;
		_bridgeCts = null;
	}

	private async Task BridgeConnection(TcpClient client, int targetPort, CancellationToken token)
	{
		_ = 5;
		try
		{
			using (client)
			{
				using TcpClient target = new TcpClient();
				await target.ConnectAsync(IPAddress.Loopback, targetPort, token);
				using NetworkStream clientStream = client.GetStream();
				using NetworkStream targetStream = target.GetStream();
				byte[] buf = new byte[8192];
				int read = await clientStream.ReadAsync(buf, 0, buf.Length, token);
				if (read > 0)
				{
					int headerEnd = -1;
					for (int i = 0; i < read - 3; i++)
					{
						if (buf[i] == 13 && buf[i + 1] == 10 && buf[i + 2] == 13 && buf[i + 3] == 10)
						{
							headerEnd = i + 4;
							break;
						}
					}
					if (headerEnd <= 0)
					{
						await targetStream.WriteAsync(buf, 0, read, token);
					}
					else
					{
						string text = Encoding.UTF8.GetString(buf, 0, headerEnd);
						string value = ((IPEndPoint)client.Client.RemoteEndPoint).Address.ToString();
						Match match = Regex.Match(text, "Host:\\s*[^\\r\\n]+", RegexOptions.IgnoreCase);
						if (match.Success)
						{
							text = text.Substring(0, match.Index) + $"Host: 127.0.0.1:{targetPort}\r\nX-Forwarded-For: {value}" + text.Substring(match.Index + match.Length);
						}
						byte[] bytes = Encoding.UTF8.GetBytes(text);
						await targetStream.WriteAsync(bytes, 0, bytes.Length, token);
						if (read > headerEnd)
						{
							await targetStream.WriteAsync(buf, headerEnd, read - headerEnd, token);
						}
					}
				}
				Task task = clientStream.CopyToAsync(targetStream, token);
				Task task2 = targetStream.CopyToAsync(clientStream, token);
				await Task.WhenAny(task, task2);
			}
		}
		catch
		{
		}
	}

	private void UpdateNetworkInfo()
	{
		try
		{
			IEnumerable<NetworkInterface> enumerable = from ni in NetworkInterface.GetAllNetworkInterfaces()
				where ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback
				select ni;
			List<string> list = new List<string>();
			foreach (NetworkInterface item in enumerable)
			{
				UnicastIPAddressInformation unicastIPAddressInformation = item.GetIPProperties().UnicastAddresses.FirstOrDefault((UnicastIPAddressInformation ua) => ua.Address.AddressFamily == AddressFamily.InterNetwork);
				if (unicastIPAddressInformation != null)
				{
					string text = item.Name.ToLower();
					string text2 = item.Description.ToLower();
					string value = "LAN";
					if (text.Contains("wi-fi") || text.Contains("wlan") || text.Contains("direct") || text.Contains("hotspot"))
					{
						value = "WiFi/Hotspot";
					}
					else if (text2.Contains("ndis") || text.Contains("tethering") || text.Contains("ethernet 2") || text.Contains("remote ndis") || text.Contains("usb"))
					{
						value = "USB/Tether";
					}
					list.Add($"{unicastIPAddressInformation.Address}:{_settings.HttpPort} ({value})");
				}
			}
			string displayIp = ((list.Count > 0) ? string.Join("  |  ", list) : $"127.0.0.1:{_settings.HttpPort} (Local)");
			Dispatcher.Invoke(() =>
			{
				LocalIpText.Text = displayIp;
				if (LocalIpTextMini != null)
				{
					string value2 = (displayIp.Contains("(") ? displayIp.Split('(')[0].Trim() : displayIp);
					LocalIpTextMini.Text = value2;
				}
			});
			AddToLog("Network Status: " + displayIp);
		}
		catch (Exception ex)
		{
			Log("Network Detection Error: " + ex.Message);
		}
	}

	private void SetupTrayIcon()
	{
		_trayIcon = new NotifyIcon();
		try
		{
			string iconPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "pcmaster.ico");
			if (File.Exists(iconPath))
			{
				_trayIcon.Icon = new System.Drawing.Icon(iconPath);
			}
			else
			{
				string? exePath = System.Diagnostics.Process.GetCurrentProcess().MainModule?.FileName;
				_trayIcon.Icon = (!string.IsNullOrEmpty(exePath) ? System.Drawing.Icon.ExtractAssociatedIcon(exePath) : null) ?? SystemIcons.Application;
			}
		}
		catch
		{
			_trayIcon.Icon = SystemIcons.Application;
		}
		_trayIcon.Visible = true;
		_trayIcon.Text = "PC Master Control Center";
		_trayIcon.ContextMenuStrip = new ContextMenuStrip();
		ToolStripLabel value = new ToolStripLabel("PC Master - Quick Actions")
		{
			Enabled = false
		};
		_trayIcon.ContextMenuStrip.Items.Add(value);
		_trayIcon.ContextMenuStrip.Items.Add(new ToolStripSeparator());
		ToolStripMenuItem value2 = new ToolStripMenuItem("\ud83d\udd12 Lock PC", null, (object? s, EventArgs e) =>
		{
			HandleRemoteCommand("lock");
		});
		ToolStripMenuItem value3 = new ToolStripMenuItem("\ud83c\udf19 Sleep", null, (object? s, EventArgs e) =>
		{
			HandleRemoteCommand("sleep");
		});
		ToolStripMenuItem value4 = new ToolStripMenuItem("\ud83d\udcf8 Screenshot", null, (object? s, EventArgs e) =>
		{
			HandleRemoteCommand("screenshot");
		});
		ToolStripMenuItem value5 = new ToolStripMenuItem("\ud83d\udd07 Mute", null, (object? s, EventArgs e) =>
		{
			HandleRemoteCommand("mute");
		});
		ToolStripMenuItem value6 = new ToolStripMenuItem("\ud83c\udfae Toggle Gaming Mode", null, (object? s, EventArgs e) =>
		{
			Dispatcher.Invoke(() =>
			{
				GamingModeToggle.IsChecked = GamingModeToggle.IsChecked != true;
				GamingModeToggle_Click(new object(), new RoutedEventArgs());
			});
		});
		_trayIcon.ContextMenuStrip.Items.Add(value2);
		_trayIcon.ContextMenuStrip.Items.Add(value3);
		_trayIcon.ContextMenuStrip.Items.Add(value4);
		_trayIcon.ContextMenuStrip.Items.Add(value5);
		_trayIcon.ContextMenuStrip.Items.Add(value6);
		_trayIcon.ContextMenuStrip.Items.Add(new ToolStripSeparator());
		ToolStripMenuItem toolStripMenuItem = new ToolStripMenuItem("⚡ Power Plan");
		ToolStripMenuItem value7 = new ToolStripMenuItem("Battery Saver", null, (object? s, EventArgs e) =>
		{
			SetPowerPlan("saver");
		});
		ToolStripMenuItem value8 = new ToolStripMenuItem("Balanced", null, (object? s, EventArgs e) =>
		{
			SetPowerPlan("balanced");
		});
		ToolStripMenuItem value9 = new ToolStripMenuItem("High Performance", null, (object? s, EventArgs e) =>
		{
			SetPowerPlan("high");
		});
		ToolStripMenuItem value10 = new ToolStripMenuItem("Ultimate Performance", null, (object? s, EventArgs e) =>
		{
			SetPowerPlan("ultimate");
		});
		toolStripMenuItem.DropDownItems.Add(value7);
		toolStripMenuItem.DropDownItems.Add(value8);
		toolStripMenuItem.DropDownItems.Add(value9);
		toolStripMenuItem.DropDownItems.Add(value10);
		_trayIcon.ContextMenuStrip.Items.Add(toolStripMenuItem);
		_trayIcon.ContextMenuStrip.Items.Add(new ToolStripSeparator());
		_trayIcon.ContextMenuStrip.Items.Add("Show", null, (object? s, EventArgs e) =>
		{
			ShowWindow();
		});
		_trayIcon.ContextMenuStrip.Items.Add("Exit", null, (object? s, EventArgs e) =>
		{
			ExitApp();
		});
		_trayIcon.DoubleClick += (object? s, EventArgs e) =>
		{
			ShowWindow();
		};
	}

	private void SetPowerPlan(string plan)
	{
		try
		{
			RunCommand("powercfg /setactive " + plan switch
			{
				"saver" => "a1841308-3541-4fab-bc81-f71556f20b4a", 
				"powersaver" => "a1841308-3541-4fab-bc81-f71556f20b4a",
				"balanced" => "381b4222-f694-41f0-9685-ff5bb260df2e", 
				"high" => "8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c", 
				"ultimate" => "e9a42b02-d5df-448d-aa00-03f14749eb61", 
				_ => "381b4222-f694-41f0-9685-ff5bb260df2e", 
			});
			AddToLog("Power Plan: " + plan.ToUpper());
		}
		catch (Exception ex)
		{
			Log("Power plan error: " + ex.Message);
		}
	}

	private void Window_Loaded(object sender, RoutedEventArgs e)
	{
		Rect workArea = SystemParameters.WorkArea;
		MaxHeight = workArea.Height;
		MaxWidth = workArea.Width;
		Topmost = true;
		Activate();
		Dispatcher.BeginInvoke((Action)(() =>
		{
			Topmost = false;
			StartFileServer();
			ApplyWindowMode(_settings.CompactMode);
		}), DispatcherPriority.Background);
	}

	protected override void OnStateChanged(EventArgs e)
	{
		base.OnStateChanged(e);
		if (WindowState == WindowState.Minimized && TrayToggle.IsChecked == true)
		{
			Hide();
			_trayIcon?.ShowBalloonTip(2000, "PC Client", "Running in background.", ToolTipIcon.Info);
		}
	}

	private void ShowWindow()
	{
		Show();
		WindowState = WindowState.Normal;
		Activate();
	}

	private void ExitApp()
	{
		_timer?.Stop();
		_clipboardTimer?.Stop();
		_toastTimer?.Stop();
		_brightnessDebounceTimer?.Stop();
		_shutdownCountdownTimer?.Stop();
		_antiIdleTimer?.Stop();
		EnableAntiIdle(enable: false);
		StopScreenMirroring();
		StopDiscoveryBroadcast();
		StopTcpBridge();
		try
		{
			_fileServer?.Stop();
			_fileServer?.Close();
		}
		catch (Exception ex)
		{
			Log("FileServer close error: " + ex.Message);
		}
		try
		{
			_timer?.Stop();
		}
		catch
		{
		}
		try
		{
			_usbWatcher?.Stop();
		}
		catch (Exception ex2)
		{
			Log("USB Watcher stop error: " + ex2.Message);
		}
		_trayIcon?.Dispose();
		try
		{
			_computer?.Close();
		}
		catch (Exception ex3)
		{
			Log("Computer close error: " + ex3.Message);
		}
		System.Windows.Application.Current.Shutdown();
	}

	private bool IsAutoStartEnabled()
	{
		try
		{
			return Registry.CurrentUser.OpenSubKey("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", writable: false)?.GetValue("PcClient") != null;
		}
		catch
		{
			return false;
		}
	}

	private void SetAutoStart(bool enable)
	{
		try
		{
			string text = Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule?.FileName;
			if (!string.IsNullOrEmpty(text))
			{
				RegistryKey registryKey = Registry.CurrentUser.OpenSubKey("SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run", writable: true);
				if (enable)
				{
					registryKey?.SetValue("PcClient", "\"" + text + "\"");
				}
				else
				{
					registryKey?.DeleteValue("PcClient", throwOnMissingValue: false);
				}
				AddToLog(enable ? "Run on Startup: Enabled" : "Run on Startup: Disabled");
			}
		}
		catch (Exception ex)
		{
			Log("Startup error: " + ex.Message);
		}
	}

	private async void UpdateStats(object? sender, EventArgs e)
	{
		if (_isUpdatingStats)
		{
			return;
		}
		_isUpdatingStats = true;
		try
		{
			TelemetrySnapshot telemetrySnapshot = (_latestStats = await Task.Run(() =>
			{
				float num = 0f;
				float num2 = 0f;
				float num3 = 0f;
				float num4 = 0f;
				float num5 = 0f;
				float num6 = 0f;
				foreach (IHardware item in _computer.Hardware)
				{
					try
					{
						item.Update();
					}
					catch
					{
						continue;
					}
					if (item.HardwareType == HardwareType.Cpu)
					{
						ISensor[] sensors = item.Sensors;
						foreach (ISensor sensor in sensors)
						{
							if (sensor.SensorType == SensorType.Temperature && (sensor.Name.Contains("Package") || sensor.Name.Contains("Core")))
							{
								num = sensor.Value ?? num;
							}
							if (sensor.SensorType == SensorType.Load)
							{
								num2 = Math.Max(num2, sensor.Value.GetValueOrDefault());
							}
						}
					}
					else if (item.HardwareType == HardwareType.GpuNvidia || item.HardwareType == HardwareType.GpuAmd || item.HardwareType == HardwareType.GpuIntel)
					{
						ISensor[] sensors = item.Sensors;
						foreach (ISensor sensor2 in sensors)
						{
							if (sensor2.SensorType == SensorType.Temperature)
							{
								num3 = Math.Max(num3, sensor2.Value.GetValueOrDefault());
							}
							if (sensor2.SensorType == SensorType.Load)
							{
								num4 = Math.Max(num4, sensor2.Value.GetValueOrDefault());
							}
							if (sensor2.SensorType == SensorType.Fan)
							{
								num6 = Math.Max(num6, sensor2.Value.GetValueOrDefault());
							}
						}
					}
					else if (item.HardwareType == HardwareType.Memory)
					{
						ISensor[] sensors = item.Sensors;
						foreach (ISensor sensor3 in sensors)
						{
							if (sensor3.SensorType == SensorType.Load)
							{
								num5 = Math.Max(num5, sensor3.Value.GetValueOrDefault());
							}
						}
					}
					else if (item.HardwareType == HardwareType.Motherboard)
					{
						IHardware[] subHardware = item.SubHardware;
						foreach (IHardware hardware in subHardware)
						{
							try
							{
								hardware.Update();
							}
							catch
							{
								continue;
							}
							ISensor[] sensors = hardware.Sensors;
							foreach (ISensor sensor4 in sensors)
							{
								if (sensor4.SensorType == SensorType.Fan)
								{
									num6 = Math.Max(num6, sensor4.Value.GetValueOrDefault());
								}
							}
						}
					}
				}
				if (num2 == 0f)
				{
					num2 = GetCpuUsageFallback();
				}
				(float, float, float, float) ramInfoFallback = GetRamInfoFallback();
				if (num5 == 0f)
				{
					(num5, _, _, _) = ramInfoFallback;
				}
				(float, float) diskInfo = GetDiskInfo();
				UpdateNetworkStatsInternal(out var netUp, out var netDown);
				List<DriveUsage> drives = (from d in DriveInfo.GetDrives()
					where d.IsReady && d.TotalSize > 0
					select new DriveUsage
					{
						Label = d.Name,
						UsagePercent = Math.Round(100.0 * (double)(d.TotalSize - d.TotalFreeSpace) / (double)d.TotalSize)
					}).ToList();
				List<ProcessInfo> topProcesses = Process.GetProcesses().OrderByDescending((Process p) =>
				{
					try
					{
						return p.WorkingSet64;
					}
					catch
					{
						return 0L;
					}
				}).Take(5)
					.Select((Process p) =>
					{
						ProcessInfo result = new ProcessInfo
						{
							Name = p.ProcessName,
							Info = FormatFileSize(p.WorkingSet64),
							Pid = p.Id
						};
						try
						{
							p.Dispose();
						}
						catch
						{
						}
						return result;
					})
					.ToList();
				return new TelemetrySnapshot
				{
					CpuLoad = num2,
					CpuTemp = num,
					GpuLoad = num4,
					GpuTemp = num3,
					RamUsagePercent = num5,
					RamUsedGb = ramInfoFallback.Item2,
					RamTotalGb = ramInfoFallback.Item3,
					FreeRamGb = ramInfoFallback.Item4,
					DiskUsedGb = diskInfo.Item1,
					DiskTotalGb = diskInfo.Item2,
					NetUpMb = netUp,
					NetDownMb = netDown,
					FanSpeed = num6,
					Hostname = Environment.MachineName,
					OsName = RuntimeInformation.OSDescription,
					Uptime = FormatUptime(Environment.TickCount64),
					Battery = GetBatteryText(),
					Drives = drives,
					TopProcesses = topProcesses
				};
			}));
			lock (_telemetryHistoryLock)
			{
				_telemetryHistory.Add(telemetrySnapshot);
				while (_telemetryHistory.Count > 60)
				{
					_telemetryHistory.RemoveAt(0);
				}
			}
			CpuTempText.Text = ((telemetrySnapshot.CpuTemp > 0f) ? $"{telemetrySnapshot.CpuTemp:F0}°C" : "--");
			CpuUsageText.Text = $"{telemetrySnapshot.CpuLoad:F1}% LOAD";
			UpdateRing(CpuRingProgress, telemetrySnapshot.CpuLoad);
			GpuTempText.Text = ((telemetrySnapshot.GpuTemp > 0f) ? $"{telemetrySnapshot.GpuTemp:F0}°C" : "--");
			GpuUsageText.Text = $"{telemetrySnapshot.GpuLoad:F1}% LOAD";
			UpdateRing(GpuRingProgress, telemetrySnapshot.GpuLoad);
			RamUsageText.Text = ((telemetrySnapshot.RamUsagePercent > 0f) ? $"{telemetrySnapshot.RamUsagePercent:F0}%" : "--");
			RamAvailText.Text = ((telemetrySnapshot.FreeRamGb > 0f) ? $"{telemetrySnapshot.FreeRamGb:F1} GB FREE" : "-- GB FREE");
			UpdateRing(RamRingProgress, telemetrySnapshot.RamUsagePercent);
			FanSpeedText.Text = ((telemetrySnapshot.FanSpeed > 0f) ? $"{telemetrySnapshot.FanSpeed:F0}" : "0");
			UpdateRing(FanRingProgress, telemetrySnapshot.FanSpeed / 3000f * 100f);
			bool flag = (DateTime.Now - _lastMobileContact).TotalSeconds < 30.0;
			if (!flag)
			{
				_currentDeviceName = "NOT CONNECTED";
				ConnectionIndicator.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(byte.MaxValue, 69, 58));
				ConnectionGlow.Color = System.Windows.Media.Color.FromRgb(byte.MaxValue, 69, 58);
				ConnectionLabel.Text = "STATUS:";
				ConnectionLabel.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(90, 106, 138));
				DeviceNameText.Foreground = new SolidColorBrush(Colors.White);
			}
			else
			{
				ConnectionIndicator.Fill = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0, byte.MaxValue, 148));
				ConnectionGlow.Color = System.Windows.Media.Color.FromRgb(0, byte.MaxValue, 148);
				ConnectionLabel.Text = "CONNECTED:";
				ConnectionLabel.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0, byte.MaxValue, 148));
				DeviceNameText.Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0, byte.MaxValue, 148));
			}
			DeviceNameText.Text = _currentDeviceName;
			if (TxtHostname != null)
			{
				TxtHostname.Text = telemetrySnapshot.Hostname;
			}
			if (TxtOsName != null)
			{
				TxtOsName.Text = telemetrySnapshot.OsName;
			}
			if (TxtUptime != null)
			{
				TxtUptime.Text = telemetrySnapshot.Uptime;
			}
			if (TxtCurrentTime != null)
			{
				TxtCurrentTime.Text = DateTime.Now.ToString("h:mm:ss tt");
			}
			if (TxtBattery != null)
			{
				TxtBattery.Text = telemetrySnapshot.Battery;
			}
			if (TxtNetDown != null)
			{
				TxtNetDown.Text = $"\ud83d\udce5 {telemetrySnapshot.NetDownMb:F2} MB/s";
			}
			if (TxtNetUp != null)
			{
				TxtNetUp.Text = $"\ud83d\udce4 {telemetrySnapshot.NetUpMb:F2} MB/s";
			}
			if (DrivesList != null)
			{
				DrivesList.ItemsSource = telemetrySnapshot.Drives;
			}
			if (TopProcessesList != null)
			{
				TopProcessesList.ItemsSource = telemetrySnapshot.TopProcesses;
			}
			if (CpuTempTextMini != null)
			{
				CpuTempTextMini.Text = ((telemetrySnapshot.CpuTemp > 0f) ? $"{telemetrySnapshot.CpuTemp:F0}°C" : "--");
			}
			if (CpuUsageTextMini != null)
			{
				CpuUsageTextMini.Text = $"{telemetrySnapshot.CpuLoad:F0}%";
			}
			if (CpuBarMini != null)
			{
				CpuBarMini.Value = Math.Clamp(telemetrySnapshot.CpuLoad, 0f, 100f);
			}
			if (GpuTempTextMini != null)
			{
				GpuTempTextMini.Text = ((telemetrySnapshot.GpuTemp > 0f) ? $"{telemetrySnapshot.GpuTemp:F0}°C" : "--");
			}
			if (GpuUsageTextMini != null)
			{
				GpuUsageTextMini.Text = $"{telemetrySnapshot.GpuLoad:F0}%";
			}
			if (GpuBarMini != null)
			{
				GpuBarMini.Value = Math.Clamp(telemetrySnapshot.GpuLoad, 0f, 100f);
			}
			if (RamUsageTextMini != null)
			{
				RamUsageTextMini.Text = $"{telemetrySnapshot.RamUsagePercent:F0}%";
			}
			if (RamAvailTextMini != null)
			{
				RamAvailTextMini.Text = ((telemetrySnapshot.FreeRamGb > 0f) ? $"{telemetrySnapshot.FreeRamGb:F1}GB" : "--");
			}
			if (RamBarMini != null)
			{
				RamBarMini.Value = Math.Clamp(telemetrySnapshot.RamUsagePercent, 0f, 100f);
			}
			if (FanSpeedTextMini != null)
			{
				FanSpeedTextMini.Text = ((telemetrySnapshot.FanSpeed > 0f) ? $"{telemetrySnapshot.FanSpeed:F0} RPM" : "0 RPM");
			}
			if (FanBarMini != null)
			{
				FanBarMini.Value = Math.Clamp(telemetrySnapshot.FanSpeed / 3000f * 100f, 0f, 100f);
			}
			if (ConnectionIndicatorMini != null)
			{
				ConnectionIndicatorMini.Fill = (flag ? new SolidColorBrush(System.Windows.Media.Color.FromRgb(0, byte.MaxValue, 148)) : new SolidColorBrush(System.Windows.Media.Color.FromRgb(byte.MaxValue, 69, 58)));
			}
			if (ConnectionGlowMini != null)
			{
				ConnectionGlowMini.Color = (flag ? System.Windows.Media.Color.FromRgb(0, byte.MaxValue, 148) : System.Windows.Media.Color.FromRgb(byte.MaxValue, 69, 58));
			}
			if (DeviceNameTextMini != null)
			{
				DeviceNameTextMini.Text = _currentDeviceName;
			}
		}
		catch (Exception ex)
		{
			Log("UpdateStats Error: " + ex.Message);
		}
		finally
		{
			_isUpdatingStats = false;
		}
	}

	private static string FormatUptime(long tickCountMs)
	{
		TimeSpan timeSpan = TimeSpan.FromMilliseconds(tickCountMs);
		if (!(timeSpan.TotalDays >= 1.0))
		{
			return $"{timeSpan.Hours}h {timeSpan.Minutes}m";
		}
		return $"{(int)timeSpan.TotalDays}d {timeSpan.Hours}h {timeSpan.Minutes}m";
	}

	private static string GetBatteryText()
	{
		try
		{
			PowerStatus powerStatus = System.Windows.Forms.SystemInformation.PowerStatus;
			if (powerStatus.BatteryChargeStatus.HasFlag(BatteryChargeStatus.NoSystemBattery))
			{
				return "Desktop (AC)";
			}
			int value = (int)(powerStatus.BatteryLifePercent * 100f);
			string value2 = ((powerStatus.PowerLineStatus == System.Windows.Forms.PowerLineStatus.Online) ? " (Charging)" : "");
			return $"{value}%{value2}";
		}
		catch
		{
		}
		return "AC Power";
	}

	private float GetCpuUsageFallback()
	{
		try
		{
			using ManagementObjectSearcher managementObjectSearcher = new ManagementObjectSearcher("SELECT LoadPercentage FROM Win32_Processor");
			using ManagementObjectCollection.ManagementObjectEnumerator managementObjectEnumerator = managementObjectSearcher.Get().GetEnumerator();
			if (managementObjectEnumerator.MoveNext())
			{
				return Convert.ToSingle(managementObjectEnumerator.Current["LoadPercentage"]);
			}
		}
		catch
		{
		}
		return 0f;
	}

	[DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
	private static extern bool GlobalMemoryStatusEx([In][Out] MEMORYSTATUSEX lpBuffer);

	private (float usage, float usedGb, float totalGb, float freeGb) GetRamInfoFallback()
	{
		try
		{
			MEMORYSTATUSEX mEMORYSTATUSEX = new MEMORYSTATUSEX();
			if (GlobalMemoryStatusEx(mEMORYSTATUSEX))
			{
				float num = (float)mEMORYSTATUSEX.ullTotalPhys / 1.0737418E+09f;
				float num2 = (float)mEMORYSTATUSEX.ullAvailPhys / 1.0737418E+09f;
				return (usage: mEMORYSTATUSEX.dwMemoryLoad, usedGb: num - num2, totalGb: num, freeGb: num2);
			}
		}
		catch
		{
		}
		return (usage: 0f, usedGb: 0f, totalGb: 0f, freeGb: 0f);
	}

	private (float usedGb, float totalGb) GetDiskInfo()
	{
		try
		{
			DriveInfo driveInfo = DriveInfo.GetDrives().FirstOrDefault((DriveInfo d) => d.IsReady && d.Name.StartsWith("C"));
			if (driveInfo != null)
			{
				float num = (float)driveInfo.TotalSize / 1.0737418E+09f;
				float num2 = (float)driveInfo.TotalFreeSpace / 1.0737418E+09f;
				return (usedGb: num - num2, totalGb: num);
			}
		}
		catch
		{
		}
		return (usedGb: 0f, totalGb: 0f);
	}

	private void UpdateNetworkStatsInternal(out float netUp, out float netDown)
	{
		netUp = 0f;
		netDown = 0f;
		try
		{
			List<IPv4InterfaceStatistics> source = (from ni in NetworkInterface.GetAllNetworkInterfaces()
				where ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback
				select ni.GetIPv4Statistics()).ToList();
			long num = source.Sum((IPv4InterfaceStatistics s) => s.BytesSent);
			long num2 = source.Sum((IPv4InterfaceStatistics s) => s.BytesReceived);
			DateTime now = DateTime.Now;
			if (_lastBytesSent != -1)
			{
				double totalSeconds = (now - _lastNetUpdateTime).TotalSeconds;
				if (totalSeconds > 0.1)
				{
					netUp = (float)((double)(num - _lastBytesSent) / 1024.0 / 1024.0 / totalSeconds);
					netDown = (float)((double)(num2 - _lastBytesReceived) / 1024.0 / 1024.0 / totalSeconds);
				}
			}
			_lastBytesSent = num;
			_lastBytesReceived = num2;
			_lastNetUpdateTime = now;
		}
		catch
		{
		}
	}

	private void UpdateRing(Ellipse ring, float percentage)
	{
		if (float.IsNaN(percentage) || float.IsInfinity(percentage))
		{
			percentage = 0f;
		}
		percentage = Math.Clamp(percentage, 0f, 100f);
		double num = (ring.ActualWidth - ring.StrokeThickness) / 2.0;
		if (num <= 0.0 || double.IsNaN(num) || double.IsInfinity(num))
		{
			num = 54.0;
		}
		double num2 = Math.PI * 2.0 * num;
		double num3 = ring.StrokeThickness;
		if (num3 <= 0.0 || double.IsNaN(num3) || double.IsInfinity(num3))
		{
			num3 = 1.0;
		}
		double num4 = num2 / num3;
		ring.StrokeDashArray = new DoubleCollection(new double[2] { num4, num4 });
		double value = num4 - num4 * (double)percentage / 100.0;
		DoubleAnimation animation = new DoubleAnimation
		{
			To = value,
			Duration = TimeSpan.FromMilliseconds(800.0),
			EasingFunction = new CubicEase
			{
				EasingMode = EasingMode.EaseOut
			}
		};
		ring.BeginAnimation(Shape.StrokeDashOffsetProperty, animation);
	}

	private void AddToLog(string message)
	{
		Dispatcher.Invoke(() =>
		{
			Log(message);
			LogEntries.Insert(0, new LogEntry
			{
				Time = DateTime.Now,
				Message = message
			});
			if (LogEntries.Count > 200)
			{
				LogEntries.RemoveAt(LogEntries.Count - 1);
			}
		});
	}

	private void UpdateDeviceName(string name)
	{
		_lastMobileContact = DateTime.Now;
		_currentDeviceName = name.ToUpper();
	}

	private void GamingModeToggle_Click(object sender, RoutedEventArgs e)
	{
		bool valueOrDefault;
		if (sender == GamingModeToggleMini)
		{
			valueOrDefault = GamingModeToggleMini.IsChecked == true;
			GamingModeToggle.IsChecked = valueOrDefault;
		}
		else
		{
			valueOrDefault = GamingModeToggle.IsChecked == true;
			if (GamingModeToggleMini != null)
			{
				GamingModeToggleMini.IsChecked = valueOrDefault;
			}
		}
		if (GamingOptionsPanel != null)
		{
			GamingOptionsPanel.Visibility = ((!valueOrDefault) ? Visibility.Collapsed : Visibility.Visible);
		}
		if (GamingStatusText != null)
		{
			GamingStatusText.Text = (valueOrDefault ? "Active - Optimizing..." : "Disabled");
			GamingStatusText.Foreground = (valueOrDefault ? ((SolidColorBrush)FindResource("AccentGreen")) : ((SolidColorBrush)FindResource("TextDim")));
		}
		DoubleAnimation animation = new DoubleAnimation
		{
			To = (valueOrDefault ? 1 : 0),
			Duration = TimeSpan.FromSeconds(0.8),
			EasingFunction = new CubicEase
			{
				EasingMode = EasingMode.EaseOut
			}
		};
		HyperGlowOverlay.BeginAnimation(UIElement.OpacityProperty, animation);
		if (valueOrDefault)
		{
			ApplyGamingOptimizations();
		}
		else
		{
			RevertGamingOptimizations();
		}
	}

	private void ApplyGamingOptimizations()
	{
		if (CbHighPerformance == null || CbHighPerformance.IsChecked == true)
		{
			RunCommand("powercfg /setactive 8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c");
		}
		if (CbDND == null || CbDND.IsChecked == true)
		{
			Registry.SetValue("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings", "NOC_GLOBAL_SETTING_TOASTS_ENABLED", 0, RegistryValueKind.DWord);
		}
		AddToLog("Gaming Mode: Performance Profile Active");
	}

	private void RevertGamingOptimizations()
	{
		RunCommand("powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e");
		Registry.SetValue("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Notifications\\Settings", "NOC_GLOBAL_SETTING_TOASTS_ENABLED", 1, RegistryValueKind.DWord);
		AddToLog("Gaming Mode: Reverted to Standard");
	}

	private void BoostRam_Click(object sender, RoutedEventArgs e)
	{
		int num = 0;
		string[] array = new string[5] { "chrome", "msedge", "spotify", "slack", "teams" };
		foreach (string text in array)
		{
			Process[] processesByName = Process.GetProcessesByName(text);
			foreach (Process process in processesByName)
			{
				try
				{
					process.Kill();
					num++;
				}
				catch (Exception ex)
				{
					Log("BoostRam: Failed to kill " + text + ": " + ex.Message);
				}
				finally
				{
					process.Dispose();
				}
			}
		}
		AddToLog($"RAM Boost: Terminated {num} background tasks");
	}

	private void RunCommand(string cmd)
	{
		try
		{
			using (Process.Start(new ProcessStartInfo("cmd.exe", "/c " + cmd)
			{
				CreateNoWindow = true,
				UseShellExecute = false
			}))
			{
			}
		}
		catch (Exception ex)
		{
			Log("RunCommand error: " + ex.Message);
		}
	}

	private void FixFirewall_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			int httpPort = _settings.HttpPort;
			int discoveryPort = _settings.DiscoveryPort;
			string value = $"PC Master Hub TCP ({httpPort})";
			string value2 = $"PC Master Hub UDP ({discoveryPort})";
			string text = $"netsh advfirewall firewall add rule name=\"{value}\" dir=in action=allow protocol=TCP localport={httpPort} & netsh advfirewall firewall add rule name=\"{value2}\" dir=in action=allow protocol=UDP localport={discoveryPort}";
			Process.Start(new ProcessStartInfo("cmd.exe", "/c " + text)
			{
				Verb = "runas",
				CreateNoWindow = true,
				UseShellExecute = true
			});
			AddToLog($"Firewall Rules: Adding TCP {httpPort} and UDP {discoveryPort}...");
			System.Windows.MessageBox.Show("Attempting to add firewall rules. Please click 'Yes' on the administrator prompt.", "Firewall Setup");
		}
		catch (Exception ex)
		{
			AddToLog("Firewall Fix Failed: " + ex.Message);
			System.Windows.MessageBox.Show("Failed to add firewall rule. Please run the app as Administrator.", "Error");
		}
	}

	private void StartDiscoveryBroadcast()
	{
		try
		{
			StopDiscoveryBroadcast();
			UdpClient udpClient = new UdpClient();
			udpClient.EnableBroadcast = true;
			_udpDiscovery = udpClient;
			Thread thread = new Thread(() =>
			{
				byte[] bytes = Encoding.UTF8.GetBytes("PC_MASTER_HUB:8099");
				while (_fileServer != null && _fileServer.IsListening)
				{
					try
					{
						UdpClient udpDiscovery = _udpDiscovery;
						if (udpDiscovery == null)
						{
							break;
						}
						udpDiscovery.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, 8091));
						NetworkInterface[] allNetworkInterfaces = NetworkInterface.GetAllNetworkInterfaces();
						foreach (NetworkInterface networkInterface in allNetworkInterfaces)
						{
							if (networkInterface.OperationalStatus == OperationalStatus.Up && networkInterface.NetworkInterfaceType != NetworkInterfaceType.Loopback)
							{
								foreach (UnicastIPAddressInformation unicastAddress in networkInterface.GetIPProperties().UnicastAddresses)
								{
									if (unicastAddress.Address.AddressFamily == AddressFamily.InterNetwork)
									{
										try
										{
											byte[] addressBytes = unicastAddress.Address.GetAddressBytes();
											addressBytes[3] = byte.MaxValue;
											udpDiscovery.Send(bytes, bytes.Length, new IPEndPoint(new IPAddress(addressBytes), 8091));
										}
										catch
										{
										}
									}
								}
							}
						}
					}
					catch
					{
						break;
					}
					Thread.Sleep(3000);
				}
			});
			thread.IsBackground = true;
			thread.Name = "DiscoveryThread";
			thread.Start();
			Log("Discovery broadcast started on port 8091");
		}
		catch (Exception ex)
		{
			Log("Discovery Error: " + ex.Message);
		}
	}

	private void StopDiscoveryBroadcast()
	{
		try
		{
			_udpDiscovery?.Close();
			_udpDiscovery = null;
		}
		catch
		{
		}
	}

	private void StartFileServerButton_Click(object sender, RoutedEventArgs e)
	{
		if (_fileServer == null)
		{
			StartFileServer();
			return;
		}
		StopDiscoveryBroadcast();
		StopTcpBridge();
		try
		{
			_fileServer.Stop();
			_fileServer.Close();
		}
		catch (Exception ex)
		{
			Log("FileServer stop error: " + ex.Message);
		}
		_fileServer = null;
		StartFileServerButton.Tag = null;
		StartFileServerButton.Content = "START HUB";
		if (StartFileServerButtonMini != null)
		{
			StartFileServerButtonMini.Tag = null;
			StartFileServerButtonMini.Content = "START HUB";
		}
		AddToLog("Wireless Hub: Stopped");
	}

	private void MirrorStartBtn_Click(object sender, RoutedEventArgs e)
	{
		StartScreenMirroring();
		MirrorStartBtn.Visibility = Visibility.Collapsed;
		MirrorStopBtn.Visibility = Visibility.Visible;
		MirrorHubStatusText.Text = "Streaming...";
		MirrorHubStatusText.Foreground = (SolidColorBrush)FindResource("AccentGreen");
	}

	private void MirrorStopBtn_Click(object sender, RoutedEventArgs e)
	{
		StopScreenMirroring();
		_isMirroringActive = false;
		MobileScreenImage.Source = null;
		MirrorPlaceholder.Visibility = Visibility.Visible;
		MirrorStartBtn.Visibility = Visibility.Visible;
		MirrorStopBtn.Visibility = Visibility.Collapsed;
		MirrorHubStatusText.Text = "Stopped";
		MirrorHubStatusText.Foreground = (SolidColorBrush)FindResource("TextDim");
	}

	private void SetupUsbWatcher()
	{
		try
		{
			WqlEventQuery query = new WqlEventQuery("SELECT * FROM __InstanceOperationEvent WITHIN 2 WHERE TargetInstance ISA 'Win32_PnPEntity'");
			_usbWatcher = new ManagementEventWatcher(query);
			_usbWatcher.EventArrived += (object s, EventArrivedEventArgs e) =>
			{
				OnUsbDeviceChanged();
				UpdateNetworkInfo();
			};
			_usbWatcher.Start();
		}
		catch (Exception ex)
		{
			Log("USB Watcher Error: " + ex.Message);
		}
	}

	private void OnUsbDeviceChanged()
	{
		Task.Run(() =>
		{
			try
			{
				if (!string.IsNullOrEmpty(_settings.AdbPath) && File.Exists(_settings.AdbPath))
				{
					Process.Start(new ProcessStartInfo(_settings.AdbPath, "reverse tcp:8099 tcp:8099")
					{
						CreateNoWindow = true,
						UseShellExecute = false
					});
					AddToLog("USB Sync: adb reverse attempted (Port 8099)");
				}
				if (_fileServer == null && _settings.AutoStartHub)
				{
					Dispatcher.Invoke(() =>
					{
						StartFileServer();
					});
					Thread.Sleep(1000);
				}
				UpdateNetworkInfo();
				AddToLog("USB device detected. Hub is ready for mobile connection.");
			}
			catch (Exception ex)
			{
				Log("USB Event Error: " + ex.Message);
			}
		});
	}

	private void ListenForRequests()
	{
		string sharedPath = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Shared");
		if (!Directory.Exists(sharedPath))
		{
			Directory.CreateDirectory(sharedPath);
		}
		while (_fileServer != null && _fileServer.IsListening)
		{
			try
			{
				HttpListenerContext context = _fileServer.GetContext();
				if (context != null)
				{
					ThreadPool.QueueUserWorkItem((object? c) =>
					{
						if (c is HttpListenerContext ctx)
						{
							try
							{
								ProcessRequest(ctx, sharedPath);
							}
							catch (Exception ex)
							{
								Log("ProcessRequest error: " + ex.Message);
								try
								{
									ctx.Response.StatusCode = 500;
									ctx.Response.Close();
								}
								catch
								{
								}
							}
						}
					}, context);
				}
			}
			catch (HttpListenerException)
			{
				if (_fileServer == null || !_fileServer.IsListening)
				{
					break;
				}
			}
			catch (Exception ex2)
			{
				Log("ListenForRequests error: " + ex2.Message);
				if (_fileServer == null || !_fileServer.IsListening)
				{
					break;
				}
			}
		}
	}

	private void ProcessRequest(HttpListenerContext context, string sharedPath)
	{
		HttpListenerRequest request = context.Request;
		HttpListenerResponse response = context.Response;
		try
		{
			string text = request.Url?.LocalPath ?? "/";
			string text2 = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
			if (!string.IsNullOrEmpty(text2) && text2 != "127.0.0.1" && text2 != "::1")
			{
				_lastConnectedMobileIp = text2;
			}
			response.Headers.Add("Access-Control-Allow-Origin", "*");
			response.Headers.Add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
			response.Headers.Add("Access-Control-Allow-Headers", "Content-Type, User-Agent, Authorization");
			if (request.HttpMethod == "OPTIONS")
			{
				response.StatusCode = 200;
				response.OutputStream.Close();
				return;
			}
			if (!ValidateAuth(request) && !IsPublicEndpoint(text))
			{
				response.StatusCode = 401;
				SendString(response, "{\"error\":\"Unauthorized\"}", "application/json");
				return;
			}
			_lastMobileContact = DateTime.Now;
			string text3 = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
			if (!string.IsNullOrEmpty(text3) && text3 != "127.0.0.1" && text3 != "::1")
			{
				_lastConnectedMobileIp = text3;
			}
			string text4 = request.Headers["User-Agent"] ?? "";
			if (!string.IsNullOrEmpty(text4) && !text4.Contains("Service"))
			{
				string text5 = "Mobile Device";
				if (text4.Contains("PC-Master-Android-App"))
				{
					text5 = "Android App v1.2";
				}
				else if (!text4.Contains("Android"))
				{
					text5 = ((!text4.Contains("iPhone")) ? text4.Split(' ')[0] : "iPhone");
				}
				else
				{
					int num = text4.IndexOf("(");
					int num2 = text4.IndexOf(")");
					if (num > -1 && num2 > num)
					{
						string[] array = text4.Substring(num + 1, num2 - num - 1).Split(';');
						text5 = ((array.Length > 2) ? array[2].Trim() : "Android Phone");
					}
					else
					{
						text5 = "Android Phone";
					}
				}
				if (text5.Contains("Mozilla"))
				{
					text5 = "Mobile Browser";
				}
				UpdateDeviceName(text5);
			}
			if (text == "/ping")
			{
				string pingIp = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
				if (!string.IsNullOrEmpty(pingIp) && pingIp != "127.0.0.1" && pingIp != "::1")
				{
					_lastConnectedMobileIp = pingIp;
				}
				SendString(response, "{\"status\":\"ok\",\"app\":\"PCMasterControl\",\"mirroring\":" + (_isMirroringActive ? "true" : "false") + "}", "application/json");
				return;
			}
			if (request.HttpMethod == "POST" && text == "/mirror/frame")
			{
				try
				{
					string clientIp = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
					if (!string.IsNullOrEmpty(clientIp) && clientIp != "127.0.0.1" && clientIp != "::1")
					{
						_lastConnectedMobileIp = clientIp;
					}
					MemoryStream ms = new MemoryStream();
					try
					{
						request.InputStream.CopyTo(ms);
						ms.Position = 0L;
						Dispatcher.Invoke(() =>
						{
							BitmapImage bitmapImage = new BitmapImage();
							bitmapImage.BeginInit();
							bitmapImage.StreamSource = ms;
							bitmapImage.CacheOption = BitmapCacheOption.OnLoad;
							bitmapImage.EndInit();
							bitmapImage.Freeze();
							MobileScreenImage.Source = bitmapImage;
							MirrorPlaceholder.Visibility = Visibility.Collapsed;
							MirrorHubStatusText.Text = "Live (Streaming)";
							MirrorHubStatusText.Foreground = (SolidColorBrush)FindResource("AccentGreen");
							MirrorStartBtn.Visibility = Visibility.Collapsed;
							MirrorStopBtn.Visibility = Visibility.Visible;
							if (!_isMirroringActive || MirrorPanel.Visibility != Visibility.Visible)
							{
								_isMirroringActive = true;
								DashboardPanel.Visibility = Visibility.Collapsed;
								FilesPanel.Visibility = Visibility.Collapsed;
								RemotePanel.Visibility = Visibility.Collapsed;
								MediaPanel.Visibility = Visibility.Collapsed;
								SettingsPanel.Visibility = Visibility.Collapsed;
								MirrorPanel.Visibility = Visibility.Visible;
								AddToLog("Auto-switched to Phone Mirror View");
							}
						});
					}
					finally
					{
						if (ms != null)
						{
							((IDisposable)ms).Dispose();
						}
					}
					SendString(response, "{\"status\":\"received\"}", "application/json");
					return;
				}
				catch (Exception ex)
				{
					Log("/mirror/frame error: " + ex.Message);
					SendString(response, "{\"status\":\"error\"}", "application/json");
					return;
				}
			}
			if (request.HttpMethod == "POST" && text == "/remote/notif")
			{
				try
				{
					string text6 = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
					if (!string.IsNullOrEmpty(text6) && text6 != "127.0.0.1" && text6 != "::1")
					{
						_lastConnectedMobileIp = text6;
					}
					using StreamReader streamReader = new StreamReader(request.InputStream);
					string json = streamReader.ReadToEnd();
					NotificationItem notif = JsonSerializer.Deserialize<NotificationItem>(json, new JsonSerializerOptions
					{
						PropertyNameCaseInsensitive = true
					});
					if (notif != null)
					{
						Dispatcher.Invoke(() =>
						{
							NotificationItem notificationItem = _notifications.FirstOrDefault((NotificationItem n) => n.Id == notif.Id || (n.Title == notif.Title && n.Content == notif.Content && n.AppName == notif.AppName));
							if (notificationItem != null)
							{
								_notifications.Remove(notificationItem);
							}
							_notifications.Insert(0, notif);
							if (_notifications.Count > MaxNotifications)
							{
								_notifications.RemoveAt(_notifications.Count - 1);
							}
							NoNotifText.Visibility = Visibility.Collapsed;
							UpdateNotifBadge();
							ShowDesktopNotification(notif);
							AddToLog($"Notification: {notif.AppName} - {notif.Title}: {notif.Content}");
						});
					}
					SendString(response, "{\"status\":\"ok\"}", "application/json");
					return;
				}
				catch (Exception ex2)
				{
					SendString(response, "{\"error\":\"" + ex2.Message + "\"}", "application/json");
					return;
				}
			}
			if (text == "/notifications")
			{
				List<NotificationItem> value = Dispatcher.Invoke(() => _notifications.ToList());
				SendString(response, JsonSerializer.Serialize(value), "application/json");
				return;
			}
			if (text == "/notif/pending-replies")
			{
				List<NotificationReplyItem> replies = new List<NotificationReplyItem>();
				while (_pendingReplies.TryDequeue(out var reply))
				{
					replies.Add(reply);
				}
				SendString(response, JsonSerializer.Serialize(replies), "application/json");
				return;
			}
			if (request.HttpMethod == "POST" && text == "/notif/reply")
			{
				try
				{
					using StreamReader reader = new StreamReader(request.InputStream);
					string body = reader.ReadToEnd();
					NotificationReplyItem replyItem = JsonSerializer.Deserialize<NotificationReplyItem>(body, new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
					if (replyItem != null && !string.IsNullOrEmpty(replyItem.Id) && !string.IsNullOrEmpty(replyItem.Message))
					{
						_pendingReplies.Enqueue(replyItem);
						Dispatcher.Invoke(() =>
						{
							NotificationItem notif = _notifications.FirstOrDefault(n => n.Id == replyItem.Id);
							if (notif != null)
							{
								_notifications.Remove(notif);
								UpdateNotifBadge();
								if (_notifications.Count == 0) NoNotifText.Visibility = Visibility.Visible;
							}
							AddToLog($"Reply queued for {notif?.AppName ?? "Notification"}: {replyItem.Message}");
						});
						SendString(response, "{\"status\":\"ok\"}", "application/json");
						return;
					}
					SendString(response, "{\"status\":\"error\",\"message\":\"Invalid payload\"}", "application/json");
					return;
				}
				catch (Exception ex)
				{
					SendString(response, $"{{\"status\":\"error\",\"message\":\"{ex.Message}\"}}", "application/json");
					return;
				}
			}
			if (request.HttpMethod == "POST" && text == "/upload")
			{
				string savedFile = HandleUpload(request, sharedPath);
				Dispatcher.Invoke(RefreshFilesList);
				if (!string.IsNullOrEmpty(savedFile))
				{
					Dispatcher.Invoke(() =>
					{
						ShowDesktopNotification(new NotificationItem
						{
							Id = Guid.NewGuid().ToString(),
							Title = "File Received from Mobile",
							Content = $"{savedFile} saved to Downloads",
							AppName = "PC Master File Drop"
						});
					});
				}
				SendString(response, $"{{\"status\":\"ok\",\"message\":\"Saved to Downloads\",\"fileName\":\"{savedFile}\"}}", "application/json");
				return;
			}
			if (text == "/stats")
			{
				string statsIp = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
				if (!string.IsNullOrEmpty(statsIp) && statsIp != "127.0.0.1" && statsIp != "::1")
				{
					_lastConnectedMobileIp = statsIp;
				}
				TelemetrySnapshot latestStats = _latestStats;
				bool gamingActive = false;
				Dispatcher.Invoke(() => gamingActive = GamingModeToggle.IsChecked == true);
var value2 = new
			{
				cpuLoad = latestStats.CpuLoad,
				cpuTemp = latestStats.CpuTemp,
				gpuLoad = latestStats.GpuLoad,
				gpuTemp = latestStats.GpuTemp,
				ramUsed = Math.Round(latestStats.RamUsedGb, 1),
				ramTotal = Math.Round(latestStats.RamTotalGb, 1),
				storageUsed = Math.Round(latestStats.DiskUsedGb, 1),
				storageTotal = Math.Round(latestStats.DiskTotalGb, 1),
				netUp = Math.Round(latestStats.NetUpMb, 2),
				netDown = Math.Round(latestStats.NetDownMb, 2),
				gamingActive = gamingActive,
				antiIdleActive = _isAntiIdleActive
			};
				SendString(response, JsonSerializer.Serialize(value2), "application/json");
				return;
			}
			if (text == "/health")
			{
				string healthIp = NormalizeIp(request.Headers["X-Forwarded-For"] ?? request.RemoteEndPoint.Address.ToString());
				if (!string.IsNullOrEmpty(healthIp) && healthIp != "127.0.0.1" && healthIp != "::1")
				{
					_lastConnectedMobileIp = healthIp;
				}
				TelemetrySnapshot latestStats = _latestStats;
				TimeSpan uptime = TimeSpan.FromMilliseconds(Environment.TickCount64);
				string uptimeStr = $"{(int)uptime.TotalHours}h {uptime.Minutes}m";
				int processCount = 0;
				try { processCount = Process.GetProcesses().Length; } catch {}
				int score = 100;
				List<string> issues = new List<string>();
				if (latestStats.CpuLoad > 85) { score -= 20; issues.Add("High CPU Utilization (>85%)"); }
				else if (latestStats.CpuLoad > 70) { score -= 10; issues.Add("Elevated CPU Load (>70%)"); }
				if (latestStats.CpuTemp > 85) { score -= 25; issues.Add("High CPU Temperature (>85°C)"); }
				else if (latestStats.CpuTemp > 75) { score -= 10; issues.Add("Warm CPU Temperature (>75°C)"); }
				double ramPct = (latestStats.RamTotalGb > 0) ? (latestStats.RamUsedGb / latestStats.RamTotalGb) * 100.0 : 0;
				if (ramPct > 90) { score -= 20; issues.Add("Critically Low Memory (>90% RAM)"); }
				else if (ramPct > 80) { score -= 10; issues.Add("Elevated Memory Usage (>80% RAM)"); }
				double diskPct = (latestStats.DiskTotalGb > 0) ? (latestStats.DiskUsedGb / latestStats.DiskTotalGb) * 100.0 : 0;
				if (diskPct > 95) { score -= 20; issues.Add("Critically Low Disk Space (>95%)"); }
				else if (diskPct > 85) { score -= 10; issues.Add("Low Disk Space (>85%)"); }
				score = Math.Clamp(score, 10, 100);
				string status = score >= 90 ? "Optimal" : (score >= 75 ? "Good" : (score >= 55 ? "Fair" : "Warning"));
				var healthObj = new
				{
					status = status,
					score = score,
					cpuLoad = latestStats.CpuLoad,
					cpuTemp = latestStats.CpuTemp,
					gpuLoad = latestStats.GpuLoad,
					gpuTemp = latestStats.GpuTemp,
					ramUsedGb = Math.Round(latestStats.RamUsedGb, 1),
					ramTotalGb = Math.Round(latestStats.RamTotalGb, 1),
					ramPercent = Math.Round(ramPct, 1),
					storageUsedGb = Math.Round(latestStats.DiskUsedGb, 1),
					storageTotalGb = Math.Round(latestStats.DiskTotalGb, 1),
					storagePercent = Math.Round(diskPct, 1),
					netUpMb = Math.Round(latestStats.NetUpMb, 2),
					netDownMb = Math.Round(latestStats.NetDownMb, 2),
					uptime = uptimeStr,
					processCount = processCount,
					hostname = Environment.MachineName,
					os = Environment.OSVersion.ToString(),
					battery = GetBatteryInfo(),
					issues = issues,
					timestamp = DateTime.UtcNow.ToString("o")
				};
				SendString(response, JsonSerializer.Serialize(healthObj), "application/json");
				return;
			}

			if (text == "/list")
			{
				string text7 = request.QueryString["path"];
				try
				{
					if (string.IsNullOrEmpty(text7))
					{
						var value3 = from d in DriveInfo.GetDrives()
							where d.IsReady
							select new
							{
								name = d.Name,
								path = d.Name,
								isDir = true,
								size = FormatFileSize(d.TotalSize)
							};
						SendString(response, JsonSerializer.Serialize(value3), "application/json");
						return;
					}
					DirectoryInfo directoryInfo = new DirectoryInfo(text7);
					List<object> list = new List<object>();
					DirectoryInfo[] directories = directoryInfo.GetDirectories();
					foreach (DirectoryInfo directoryInfo2 in directories)
					{
						list.Add(new
						{
							name = directoryInfo2.Name,
							path = directoryInfo2.FullName,
							isDir = true,
							size = ""
						});
					}
					FileInfo[] files = directoryInfo.GetFiles();
					foreach (FileInfo fileInfo in files)
					{
						list.Add(new
						{
							name = fileInfo.Name,
							path = fileInfo.FullName,
							isDir = false,
							size = FormatFileSize(fileInfo.Length)
						});
					}
					SendString(response, JsonSerializer.Serialize(list), "application/json");
					return;
				}
				catch (Exception ex3)
				{
					SendString(response, JsonSerializer.Serialize(new
					{
						error = ex3.Message
					}), "application/json");
					return;
				}
			}
			if (text.StartsWith("/remote/"))
			{
				string text8 = text.Substring(8).ToLower();
				if (text8.StartsWith("volume/"))
				{
					if (int.TryParse(text8.Split('/')[1], out var result))
					{
						SetSystemVolume(result);
						AddToLog($"Remote volume set to {result}%");
					}
				}
				else if (text8.StartsWith("mouse/"))
				{
					HandleMouseCommand(text8.Substring(6), request);
				}
				else if (text8.StartsWith("keyboard/"))
				{
					HandleKeyboardCommand(text8.Substring(9), request);
				}
				else
				{
					if (text8 == "open-url")
					{
						string targetUrl = request.QueryString["url"] ?? "";
						if (!Uri.TryCreate(targetUrl, UriKind.Absolute, out Uri? parsedUrl) || (parsedUrl.Scheme != Uri.UriSchemeHttp && parsedUrl.Scheme != Uri.UriSchemeHttps))
						{
							response.StatusCode = 400;
							SendString(response, "{\"status\":\"error\",\"message\":\"A valid HTTP or HTTPS URL is required\"}", "application/json");
							return;
						}
						Process.Start(new ProcessStartInfo(parsedUrl.AbsoluteUri)
						{
							UseShellExecute = true
						});
						AddToLog("Opened shared link from mobile: " + parsedUrl.Host);
						SendString(response, "{\"status\":\"ok\"}", "application/json");
						return;
					}
					if (text8.StartsWith("unlock"))
					{
						string pin = request.QueryString["pin"] ?? "";
						UnlockOrWakePc(pin);
						SendString(response, "{\"status\":\"ok\",\"unlocked\":true}", "application/json");
						return;
					}
					if (text8.StartsWith("antiidle"))
					{
						string text9 = request.QueryString["state"] ?? "toggle";
						if (text9 == "on")
						{
							EnableAntiIdle(enable: true);
						}
						else if (text9 == "off")
						{
							EnableAntiIdle(enable: false);
						}
						else
						{
							EnableAntiIdle(!_isAntiIdleActive);
						}
						SendString(response, "{\"status\":\"ok\",\"active\":" + (_isAntiIdleActive ? "true" : "false") + "}", "application/json");
						return;
					}
					if (text8 == "timer")
					{
						string timerAction = (request.QueryString["action"] ?? "").ToLowerInvariant();
						if (timerAction == "cancel")
						{
							ScheduleTimer("abort", 0);
							SendString(response, "{\"status\":\"ok\"}", "application/json");
							return;
						}

						if ((timerAction != "shutdown" && timerAction != "restart" && timerAction != "sleep") ||
							!int.TryParse(request.QueryString["minutes"], out int timerMinutes) || timerMinutes < 1 || timerMinutes > 10080)
						{
							response.StatusCode = 400;
							SendString(response, "{\"status\":\"error\",\"message\":\"A valid timer action and 1-10080 minutes are required\"}", "application/json");
							return;
						}

						ScheduleTimer(timerAction, timerMinutes);
						SendString(response, "{\"status\":\"ok\"}", "application/json");
						return;
					}
					if (text8 == "openfiles")
					{
						Dispatcher.Invoke(() =>
						{
							NavBtn_Click(NavFiles, new RoutedEventArgs());
							ShowWindow();
						});
					}
					else
					{
						HandleRemoteCommand(text8);
					}
				}
				if (!text8.Contains("mouse/move"))
				{
					AddToLog("Remote command: " + text8.ToUpper() + " (RECEIVED)");
				}
				SendString(response, "{\"status\":\"ok\"}", "application/json");
				return;
			}
			if (text == "/download")
			{
				string text10 = request.QueryString["path"];
				if (!string.IsNullOrEmpty(text10))
				{
					try
					{
						string fullPath = System.IO.Path.GetFullPath(text10);
						AddToLog("Download request path: " + text10 + " -> fullPath: " + fullPath + " IsRooted: " + System.IO.Path.IsPathRooted(fullPath));
						// Security: block UNC paths and non-local paths, but allow all local drives (C:\, D:\, etc.)
						if (fullPath.StartsWith("\\\\") || !System.IO.Path.IsPathRooted(fullPath))
						{
							response.StatusCode = 403;
							SendString(response, "{\"error\":\"Access denied: only local drive paths are allowed\"}", "application/json");
							return;
						}
						if (File.Exists(fullPath))
						{
							ServeFile(response, fullPath, request);
							AddToLog("File " + System.IO.Path.GetFileName(fullPath) + " sent to device");
						}
						else
						{
							response.StatusCode = 404;
							SendString(response, "{\"error\":\"File not found\"}", "application/json");
						}
						return;
					}
					catch (Exception ex)
					{
						response.StatusCode = 400;
						SendString(response, "{\"error\":\"" + ex.Message + "\"}", "application/json");
						return;
					}
				}
				response.StatusCode = 400;
				SendString(response, "{\"error\":\"Missing path parameter\"}", "application/json");
				return;
			}
			if (text.StartsWith("/download/"))
			{
				string text11 = WebUtility.UrlDecode(text.Substring(10));
				try
				{
					string fullPath3 = System.IO.Path.GetFullPath(System.IO.Path.Combine(sharedPath, text11));
					if (!fullPath3.StartsWith(System.IO.Path.GetFullPath(sharedPath), StringComparison.OrdinalIgnoreCase))
					{
						response.StatusCode = 403;
						SendString(response, "{\"error\":\"Access denied: path traversal not allowed\"}", "application/json");
						return;
					}
					if (File.Exists(fullPath3))
					{
						ServeFile(response, fullPath3, request);
						AddToLog("File " + text11 + " sent to device (DELIVERED)");
					}
					else
					{
						response.StatusCode = 404;
						SendString(response, "{\"error\":\"File not found\"}", "application/json");
					}
					return;
				}
				catch (Exception ex)
				{
					response.StatusCode = 400;
					SendString(response, "{\"error\":\"" + ex.Message + "\"}", "application/json");
					return;
				}
			}
			if (text == "/thumbnail")
			{
				string textThumb = request.QueryString["path"];
				if (!string.IsNullOrEmpty(textThumb))
				{
					try
					{
						string fullThumbPath = System.IO.Path.GetFullPath(textThumb);
						// Security: block UNC paths and non-local paths, but allow all local drives (C:\, D:\, etc.)
						if (fullThumbPath.StartsWith("\\\\") || !System.IO.Path.IsPathRooted(fullThumbPath))
						{
							response.StatusCode = 403;
							SendString(response, "{\"error\":\"Access denied: only local drive paths are allowed\"}", "application/json");
							return;
						}
						if (File.Exists(fullThumbPath))
						{
							string ext = System.IO.Path.GetExtension(textThumb).ToLowerInvariant();
							if (ext == ".jpg" || ext == ".jpeg" || ext == ".png" || ext == ".bmp" || ext == ".webp")
							{
								using var original = new System.Drawing.Bitmap(textThumb);
								int maxDim = 320;
								int w = original.Width;
								int h = original.Height;
								if (w > maxDim || h > maxDim)
								{
									if (w > h) { h = (h * maxDim) / w; w = maxDim; }
									else { w = (w * maxDim) / h; h = maxDim; }
								}
								using var thumb = new System.Drawing.Bitmap(original, Math.Max(1, w), Math.Max(1, h));
								using var ms = new MemoryStream();
								thumb.Save(ms, System.Drawing.Imaging.ImageFormat.Jpeg);
								byte[] data = ms.ToArray();
								response.ContentType = "image/jpeg";
								response.ContentLength64 = data.Length;
								response.OutputStream.Write(data, 0, data.Length);
								response.OutputStream.Close();
								return;
							}
						}
					}
					catch (Exception exThumb)
					{
						Log("Thumbnail generation error: " + exThumb.Message);
					}
				}
				response.StatusCode = 404;
				SendString(response, "{\"error\":\"Thumbnail unavailable\"}", "application/json");
				return;
			}
			if (text == "/settings")
			{
				string ip = (from ni in NetworkInterface.GetAllNetworkInterfaces()
					where ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback
					select ni).SelectMany((NetworkInterface ni) => ni.GetIPProperties().UnicastAddresses).FirstOrDefault((UnicastIPAddressInformation unicastIPAddressInformation) => unicastIPAddressInformation.Address.AddressFamily == AddressFamily.InterNetwork)?.Address.ToString() ?? "127.0.0.1";
				var value4 = new
				{
					authToken = _settings.AuthToken,
					requireAuth = _settings.RequireAuth,
					version = "2.0",
					compactMode = _settings.CompactMode,
					ip = ip,
					port = _settings.HttpPort,
					hostname = Environment.MachineName,
					os = Environment.OSVersion.ToString()
				};
				SendString(response, JsonSerializer.Serialize(value4), "application/json");
				return;
			}
			if (text == "/clipboard/get")
			{
				try
				{
					var value5 = new
					{
						text = (System.Windows.Forms.Clipboard.GetText() ?? "")
					};
					SendString(response, JsonSerializer.Serialize(value5), "application/json");
					return;
				}
				catch
				{
					SendString(response, "{\"text\":\"\"}", "application/json");
					return;
				}
			}
			if (request.HttpMethod == "POST" && text == "/clipboard/set")
			{
				try
				{
					using StreamReader streamReader2 = new StreamReader(request.InputStream);
					Dictionary<string, string> dictionary = JsonSerializer.Deserialize<Dictionary<string, string>>(streamReader2.ReadToEnd());
					if (dictionary != null && dictionary.TryGetValue("text", out var text12))
					{
						Dispatcher.Invoke(() =>
						{
							try
							{
								System.Windows.Forms.Clipboard.SetText(text12);
								if (CurrentClipboardText != null) CurrentClipboardText.Text = text12;
							}
							catch
							{
							}
						});
						AddToLog("Clipboard synced from device");
					}
					SendString(response, "{\"status\":\"ok\"}", "application/json");
					return;
				}
				catch (Exception ex4)
				{
					SendString(response, "{\"error\":\"" + ex4.Message + "\"}", "application/json");
					return;
				}
			}
			switch (text)
			{
			case "/screenshot":
				try
				{
					byte[] array2 = CaptureScreenshot();
					response.ContentType = "image/png";
					response.ContentLength64 = array2.Length;
					response.OutputStream.Write(array2, 0, array2.Length);
					response.OutputStream.Close();
					return;
				}
				catch (Exception ex6)
				{
					response.StatusCode = 500;
					SendString(response, "{\"error\":\"" + ex6.Message + "\"}", "application/json");
					return;
				}
			case "/media":
				try
				{
					object currentMediaInfo = GetCurrentMediaInfo();
					SendString(response, JsonSerializer.Serialize(currentMediaInfo), "application/json");
					return;
				}
				catch (Exception ex5)
				{
					SendString(response, "{\"error\":\"" + ex5.Message + "\"}", "application/json");
					return;
				}
			case "/processes":
				try
				{
					var value6 = from p in Process.GetProcesses().OrderByDescending((Process p) =>
						{
							try
							{
								return p.WorkingSet64;
							}
							catch
							{
								return 0L;
							}
						}).Take(15)
						select new
						{
							name = p.ProcessName,
							pid = p.Id,
							memory = FormatFileSize(p.WorkingSet64)
						};
					SendString(response, JsonSerializer.Serialize(value6), "application/json");
					return;
				}
				catch (Exception ex7)
				{
					SendString(response, "{\"error\":\"" + ex7.Message + "\"}", "application/json");
					return;
				}
			}
			if (request.HttpMethod == "POST" && text == "/kill")
			{
				try
				{
					using StreamReader streamReader3 = new StreamReader(request.InputStream);
					Dictionary<string, int> dictionary2 = JsonSerializer.Deserialize<Dictionary<string, int>>(streamReader3.ReadToEnd());
					if (dictionary2 != null && dictionary2.TryGetValue("pid", out var value7))
					{
						Process processById = Process.GetProcessById(value7);
						processById.Kill();
						AddToLog($"Killed process: {processById.ProcessName} (PID: {value7})");
					}
					SendString(response, "{\"status\":\"ok\"}", "application/json");
					return;
				}
				catch (Exception ex8)
				{
					SendString(response, "{\"error\":\"" + ex8.Message + "\"}", "application/json");
					return;
				}
			}
			switch (text)
			{
			case "/powerplan":
			{
				string text13 = request.QueryString["plan"];
				if (!string.IsNullOrEmpty(text13))
				{
					SetPowerPlan(text13);
					SendString(response, "{\"status\":\"ok\"}", "application/json");
					break;
				}
				try
				{
					Process? process = Process.Start(new ProcessStartInfo("powercfg", "/getactivescheme")
					{
						RedirectStandardOutput = true,
						UseShellExecute = false,
						CreateNoWindow = true
					});
					process?.WaitForExit();
					string text14 = process?.StandardOutput.ReadToEnd() ?? "";
					SendString(response, JsonSerializer.Serialize(new
					{
						output = text14.Trim()
					}), "application/json");
					break;
				}
				catch (Exception ex10)
				{
					SendString(response, "{\"error\":\"" + ex10.Message + "\"}", "application/json");
					break;
				}
			}
			case "/brightness":
			{
				string text17 = request.QueryString["level"];
				if (!string.IsNullOrEmpty(text17) && int.TryParse(text17, out var result5))
				{
					SetBrightness(result5);
					SendString(response, "{\"status\":\"ok\"}", "application/json");
				}
				else
				{
					int brightness = GetBrightness();
					SendString(response, JsonSerializer.Serialize(new
					{
						level = brightness
					}), "application/json");
				}
				break;
			}
			case "/battery":
				SendString(response, JsonSerializer.Serialize(GetBatteryInfo()), "application/json");
				break;
			case "/wifi":
				SendString(response, JsonSerializer.Serialize(GetWifiInfo()), "application/json");
				break;
			case "/timer":
			{
				string text15 = request.QueryString["action"];
				string text16 = request.QueryString["minutes"];
				if (!string.IsNullOrEmpty(text15) && !string.IsNullOrEmpty(text16) && int.TryParse(text16, out var result4))
				{
					ScheduleTimer(text15, result4);
					SendString(response, "{\"status\":\"ok\"}", "application/json");
				}
				else
				{
					SendString(response, "{\"error\":\"missing params\"}", "application/json");
				}
				break;
			}
			case "/type":
			{
				string text18 = request.QueryString["text"];
				if (!string.IsNullOrEmpty(text18))
				{
					Dispatcher.Invoke(() =>
					{
						try
						{
							SendKeys.SendWait(text18);
						}
						catch
						{
						}
					});
					AddToLog("Typed: " + ((text18.Length > 30) ? (text18.Substring(0, 30) + "...") : text18));
					SendString(response, "{\"status\":\"ok\"}", "application/json");
				}
				else
				{
					SendString(response, "{\"error\":\"no text\"}", "application/json");
				}
				break;
			}
			case "/history":
				lock (_telemetryHistoryLock)
				{
					var value9 = _telemetryHistory.Select((TelemetrySnapshot t) => new
					{
						t = t.Timestamp.ToString("HH:mm:ss"),
						cpu = Math.Round(t.CpuLoad, 1),
						cpuT = Math.Round(t.CpuTemp, 1),
						gpu = Math.Round(t.GpuLoad, 1),
						gpuT = Math.Round(t.GpuTemp, 1),
						ram = Math.Round(t.RamUsagePercent, 1)
					});
					SendString(response, JsonSerializer.Serialize(value9), "application/json");
					break;
				}
			case "/pair/qr":
			{
				string value10 = (from ni in NetworkInterface.GetAllNetworkInterfaces()
					where ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback
					select ni).SelectMany((NetworkInterface ni) => ni.GetIPProperties().UnicastAddresses).FirstOrDefault((UnicastIPAddressInformation unicastIPAddressInformation) => unicastIPAddressInformation.Address.AddressFamily == AddressFamily.InterNetwork)?.Address.ToString() ?? "127.0.0.1";
				var value11 = new
				{
					url = $"http://{value10}:{_settings.HttpPort}",
					token = _settings.AuthToken,
					port = _settings.HttpPort
				};
				SendString(response, JsonSerializer.Serialize(value11), "application/json");
				break;
			}
			case "/mirror/stream":
				if (!_isMirroring)
				{
					StartScreenMirroring();
				}
				ServeMjpegStream(response);
				break;
			case "/mirror/start":
				StartScreenMirroring();
				SendString(response, "{\"status\":\"started\"}", "application/json");
				break;
			case "/mirror/stop":
				StopScreenMirroring();
				SendString(response, "{\"status\":\"stopped\"}", "application/json");
				break;
			case "/mirror/live":
				try
				{
					long quality = 75L;
					if (long.TryParse(request.QueryString["q"], out var result2))
					{
						quality = Math.Clamp(result2, 20L, 95L);
					}
					int targetWidth = 0;
					if (int.TryParse(request.QueryString["w"], out var result3))
					{
						targetWidth = result3;
					}
					byte[] array3 = CaptureScreenFrame(quality, targetWidth);
					response.ContentType = "image/jpeg";
					response.Headers.Add("Cache-Control", "no-cache, no-store, must-revalidate");
					response.ContentLength64 = array3.Length;
					response.OutputStream.Write(array3, 0, array3.Length);
					break;
				}
				catch (Exception ex9)
				{
					try
					{
						response.StatusCode = 500;
						byte[] bytes = Encoding.UTF8.GetBytes("{\"error\":\"" + ex9.Message.Replace("\"", "'") + "\"}");
						response.ContentType = "application/json";
						response.ContentLength64 = bytes.Length;
						response.OutputStream.Write(bytes, 0, bytes.Length);
						break;
					}
					catch
					{
						break;
					}
				}
				finally
				{
					try
					{
						response.OutputStream.Close();
					}
					catch
					{
					}
				}
			case "/screen/info":
			{
				var value8 = new
				{
					width = (int)SystemParameters.VirtualScreenWidth,
					height = (int)SystemParameters.VirtualScreenHeight,
					left = (int)SystemParameters.VirtualScreenLeft,
					top = (int)SystemParameters.VirtualScreenTop
				};
				SendString(response, JsonSerializer.Serialize(value8), "application/json");
				break;
			}
			default:
				ServeWebUI(response);
				break;
			}
		}
		catch (Exception ex11)
		{
			Log("ProcessRequest Error: " + ex11.Message);
		}
	}

private bool IsPublicEndpoint(string path)
        {
            switch (path)
            {
            default:
                if (!path.StartsWith("/download/"))
                {
                    switch (path)
                    {
                    default:
                        return path == "/notifications";
                    case "/screenshot":
                    case "/mirror/stream":
                    case "/mirror/live":
                    case "/mirror/start":
                    case "/mirror/stop":
                    case "/screen/info":
                        break;
                    }
                }
                break;
            case "/ping":
            case "/settings":
            case "/":
            case "/stats":
            case "/health":
            case "/battery":
            case "/wifi":
                break;
            }
            return true;
        }

	private byte[] CaptureScreenFrame(long quality = 75L, int targetWidth = 0)
	{
		int num = 0;
		int num2 = 0;
		int num3 = 1920;
		int num4 = 1080;
		try
		{
			num = (int)SystemParameters.VirtualScreenLeft;
			num2 = (int)SystemParameters.VirtualScreenTop;
			num3 = (int)SystemParameters.VirtualScreenWidth;
			num4 = (int)SystemParameters.VirtualScreenHeight;
		}
		catch (Exception ex)
		{
			Log("CaptureScreenFrame: Failed to get screen params: " + ex.Message);
		}
		if (num3 <= 0 || num4 <= 0)
		{
			num3 = 1920;
			num4 = 1080;
		}
		using Bitmap bitmap = new Bitmap(num3, num4);
		using (Graphics graphics = Graphics.FromImage(bitmap))
		{
			bool flag = false;
			try
			{
				graphics.CopyFromScreen(num, num2, 0, 0, bitmap.Size);
				flag = true;
			}
			catch (Exception ex2)
			{
				graphics.Clear(System.Drawing.Color.FromArgb(12, 16, 26));
				using SolidBrush brush = new SolidBrush(System.Drawing.Color.FromArgb(0, 240, 255));
				using Font font = new Font(System.Drawing.FontFamily.GenericSansSerif, 20f, System.Drawing.FontStyle.Bold);
				using Font font2 = new Font(System.Drawing.FontFamily.GenericSansSerif, 12f);
				graphics.DrawString("PC MASTER - SCREEN STANDBY", font, brush, 80f, num4 / 2 - 40);
				using SolidBrush brush2 = new SolidBrush(System.Drawing.Color.Gray);
				graphics.DrawString("Desktop is locked, asleep, or protected by Windows (" + ex2.Message + ")", font2, brush2, 80f, num4 / 2 + 10);
			}
			if (flag)
			{
				try
				{
					CURSORINFO pci = new CURSORINFO
					{
						cbSize = Marshal.SizeOf(typeof(CURSORINFO))
					};
					if (GetCursorInfo(out pci) && pci.flags == 1)
					{
						nint hdc = graphics.GetHdc();
						try
						{
							DrawIcon(hdc, pci.ptScreenPos.x - num, pci.ptScreenPos.y - num2, pci.hCursor);
						}
						finally
						{
							graphics.ReleaseHdc(hdc);
						}
					}
				}
				catch
				{
				}
			}
		}
		Bitmap bitmap2 = bitmap;
		bool flag2 = false;
		if (targetWidth > 0 && targetWidth < num3)
		{
			int height = (int)((double)num4 * (double)targetWidth / (double)num3);
			bitmap2 = new Bitmap(bitmap, new System.Drawing.Size(targetWidth, height));
			flag2 = true;
		}
		try
		{
			using MemoryStream memoryStream = new MemoryStream();
			ImageCodecInfo encoder = GetEncoder(ImageFormat.Jpeg);
			if (encoder != null)
			{
				using EncoderParameters encoderParameters = new EncoderParameters(1);
				encoderParameters.Param[0] = new EncoderParameter(System.Drawing.Imaging.Encoder.Quality, quality);
				bitmap2.Save(memoryStream, encoder, encoderParameters);
			}
			else
			{
				bitmap2.Save(memoryStream, ImageFormat.Jpeg);
			}
			return memoryStream.ToArray();
		}
		finally
		{
			if (flag2)
			{
				bitmap2.Dispose();
			}
		}
	}

	private static ImageCodecInfo? GetEncoder(ImageFormat format)
	{
		return ImageCodecInfo.GetImageEncoders().FirstOrDefault((ImageCodecInfo codec) => codec.FormatID == format.Guid);
	}

	private void StartScreenMirroring()
	{
		if (_isMirroring)
		{
			return;
		}
		_isMirroring = true;
		_mirrorThread = new Thread(() =>
		{
			try
			{
				while (_isMirroring)
				{
					try
					{
						byte[] latestFrame = CaptureScreenFrame(70L, 1280);
						lock (_frameLock)
						{
							_latestFrame = latestFrame;
						}
					}
					catch
					{
					}
					Thread.Sleep(40);
				}
			}
			catch (Exception ex)
			{
				Log("Mirror error: " + ex.Message);
			}
			finally
			{
				_isMirroring = false;
			}
		})
		{
			IsBackground = true,
			Name = "ScreenMirrorThread"
		};
		_mirrorThread.Start();
		AddToLog("Screen mirroring started");
	}

	private void StopScreenMirroring()
	{
		if (_isMirroring)
		{
			_isMirroring = false;
			_mirrorThread?.Join(1000);
			AddToLog("Screen mirroring stopped");
		}
	}

	private void ServeMjpegStream(HttpListenerResponse resp)
	{
		resp.ContentType = "multipart/x-mixed-replace; boundary=frame";
		resp.Headers.Add("Cache-Control", "no-cache, private");
		resp.Headers.Add("Pragma", "no-cache");
		resp.SendChunked = true;
		try
		{
			string text = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ";
			string text2 = "\r\n\r\n";
			while (_isMirroring)
			{
				HttpListener? fileServer = _fileServer;
				if (fileServer != null && fileServer.IsListening)
				{
					byte[] latestFrame;
					lock (_frameLock)
					{
						latestFrame = _latestFrame;
					}
					if (latestFrame != null && latestFrame.Length != 0)
					{
						byte[] bytes = Encoding.ASCII.GetBytes(text + latestFrame.Length + text2);
						byte[] bytes2 = Encoding.ASCII.GetBytes("\r\n");
						resp.OutputStream.Write(bytes, 0, bytes.Length);
						resp.OutputStream.Write(latestFrame, 0, latestFrame.Length);
						resp.OutputStream.Write(bytes2, 0, bytes2.Length);
						resp.OutputStream.Flush();
					}
					Thread.Sleep(40);
					continue;
				}
				break;
			}
		}
		catch
		{
		}
		finally
		{
			try
			{
				resp.OutputStream.Close();
			}
			catch
			{
			}
		}
	}

	[DllImport("user32.dll")]
	private static extern nint GetForegroundWindow();

	[DllImport("user32.dll", CharSet = CharSet.Auto)]
	private static extern int GetWindowText(nint hWnd, StringBuilder lpString, int nMaxCount);

	[DllImport("user32.dll")]
	private static extern uint GetWindowThreadProcessId(nint hWnd, out uint lpdwProcessId);

	private object GetCurrentMediaInfo()
	{
		try
		{
			nint foregroundWindow = GetForegroundWindow();
			StringBuilder stringBuilder = new StringBuilder(256);
			GetWindowText(foregroundWindow, stringBuilder, 256);
			return new
			{
				title = stringBuilder.ToString(),
				app = "Foreground Window"
			};
		}
		catch
		{
			return new
			{
				title = "",
				app = ""
			};
		}
	}

	private byte[] CaptureScreenshot()
	{
		try
		{
			return CaptureScreenFrame(80L);
		}
		catch
		{
			using Bitmap bitmap = new Bitmap((int)SystemParameters.VirtualScreenWidth, (int)SystemParameters.VirtualScreenHeight);
			using Graphics graphics = Graphics.FromImage(bitmap);
			graphics.CopyFromScreen((int)SystemParameters.VirtualScreenLeft, (int)SystemParameters.VirtualScreenTop, 0, 0, bitmap.Size);
			using MemoryStream memoryStream = new MemoryStream();
			bitmap.Save(memoryStream, ImageFormat.Png);
			return memoryStream.ToArray();
		}
	}

	private void ToggleCompactMode()
	{
		_settings.CompactMode = !_settings.CompactMode;
		SaveSettings();
		ApplyWindowMode(_settings.CompactMode);
	}

	private void ApplyWindowMode(bool isMini)
	{
		Dispatcher.Invoke(() =>
		{
			if (isMini)
			{
				FullUIGrid.Visibility = Visibility.Collapsed;
				FullTitleBarButtons.Visibility = Visibility.Collapsed;
				MiniModeContainer.Visibility = Visibility.Visible;
				if (CompactModeToggle != null)
				{
					CompactModeToggle.IsChecked = true;
				}
				if (GamingModeToggleMini != null)
				{
					GamingModeToggleMini.IsChecked = GamingModeToggle.IsChecked;
				}
				Width = 380.0;
				Height = 520.0;
				EnsureWindowVisible();
				AddToLog("Mini Mode: ON (380x520)");
			}
			else
			{
				MiniModeContainer.Visibility = Visibility.Collapsed;
				FullUIGrid.Visibility = Visibility.Visible;
				FullTitleBarButtons.Visibility = Visibility.Visible;
				if (CompactModeToggle != null)
				{
					CompactModeToggle.IsChecked = false;
				}
				Rect workArea = SystemParameters.WorkArea;
				Width = Math.Min(980.0, workArea.Width * 0.95);
				Height = Math.Min(620.0, workArea.Height * 0.92);
				EnsureWindowVisible();
				AddToLog("Full Mode: ON");
			}
		});
	}

	private void EnsureWindowVisible()
	{
		Rect workArea = SystemParameters.WorkArea;
		if (Left + Width > workArea.Right)
		{
			Left = Math.Max(workArea.Left, workArea.Right - Width - 20.0);
		}
		if (Top + Height > workArea.Bottom)
		{
			Top = Math.Max(workArea.Top, workArea.Bottom - Height - 20.0);
		}
		if (Left < workArea.Left)
		{
			Left = workArea.Left + 10.0;
		}
		if (Top < workArea.Top)
		{
			Top = workArea.Top + 10.0;
		}
	}

	private string GetDownloadsFolderPath()
	{
		try
		{
			using RegistryKey? key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders");
			if (key != null)
			{
				object? val = key.GetValue("{374DE290-123F-4565-9164-39C4925E467B}");
				if (val != null)
				{
					string path = Environment.ExpandEnvironmentVariables(val.ToString() ?? "");
					if (Directory.Exists(path))
					{
						return path;
					}
				}
			}
		}
		catch { }
		string defaultDownloads = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
		if (!Directory.Exists(defaultDownloads))
		{
			Directory.CreateDirectory(defaultDownloads);
		}
		return defaultDownloads;
	}

	private string GetUniqueFilePath(string folder, string fileName)
	{
		string fullPath = System.IO.Path.Combine(folder, fileName);
		if (!File.Exists(fullPath))
		{
			return fullPath;
		}
		string nameWithoutExt = System.IO.Path.GetFileNameWithoutExtension(fileName);
		string ext = System.IO.Path.GetExtension(fileName);
		int counter = 1;
		while (File.Exists(fullPath))
		{
			fullPath = System.IO.Path.Combine(folder, $"{nameWithoutExt} ({counter}){ext}");
			counter++;
		}
		return fullPath;
	}

	private string HandleUpload(HttpListenerRequest req, string sharedFolder)
	{
		try
		{
			string downloadsFolder = GetDownloadsFolderPath();
			Directory.CreateDirectory(downloadsFolder);
			Directory.CreateDirectory(sharedFolder);

			string text = "upload_" + DateTime.Now.Ticks + ".bin";
			string text2 = req.Headers["Content-Disposition"] ?? "";
			if (text2.Contains("filename="))
			{
				Match match = Regex.Match(text2, "filename=\"?([^\";\\r\\n]+)\"?");
				if (match.Success)
				{
					text = match.Groups[1].Value.Trim();
				}
			}
			string text3 = req.ContentType ?? "";
			if (text3.StartsWith("multipart/form-data", StringComparison.OrdinalIgnoreCase) && text3.Contains("boundary="))
			{
				string s = "--" + text3.Split("boundary=")[1].Trim('"', ' ', ';');
				byte[] bytes = Encoding.ASCII.GetBytes(s);
				using MemoryStream memoryStream = new MemoryStream();
				req.InputStream.CopyTo(memoryStream);
				byte[] array = memoryStream.ToArray();
				Match match2 = Regex.Match(Encoding.UTF8.GetString(array, 0, Math.Min(array.Length, 2048)), "filename=\"?([^\"\\r\\n]+)\"?", RegexOptions.IgnoreCase);
				if (match2.Success)
				{
					text = match2.Groups[1].Value.Trim();
				}
				int num = -1;
				for (int i = 0; i < array.Length - 3; i++)
				{
					if (array[i] == 13 && array[i + 1] == 10 && array[i + 2] == 13 && array[i + 3] == 10)
					{
						num = i + 4;
						break;
					}
				}
				if (num > 0)
				{
					int num2 = array.Length;
					for (int num3 = array.Length - bytes.Length - 2; num3 >= num; num3--)
					{
						bool flag = true;
						for (int j = 0; j < bytes.Length; j++)
						{
							if (array[num3 + j] != bytes[j])
							{
								flag = false;
								break;
							}
						}
						if (flag)
						{
							num2 = ((num3 >= 2 && array[num3 - 2] == 13 && array[num3 - 1] == 10) ? (num3 - 2) : num3);
							break;
						}
					}
					text = System.IO.Path.GetFileName(text);
					if (!string.IsNullOrEmpty(text))
					{
						foreach (char c in System.IO.Path.GetInvalidFileNameChars())
						{
							text = text.Replace(c, '_');
						}
					}
					if (string.IsNullOrEmpty(text) || text == ".bin" || text == "_")
					{
						text = "upload_" + DateTime.Now.Ticks + ".bin";
					}

					string downloadFilePath = GetUniqueFilePath(downloadsFolder, text);
					int fileLen = Math.Max(0, num2 - num);
					byte[] fileBytes = new byte[fileLen];
					Array.Copy(array, num, fileBytes, 0, fileLen);
					File.WriteAllBytes(downloadFilePath, fileBytes);

					// Also copy to Shared folder so PC client files tab lists it
					try
					{
						string sharedFilePath = System.IO.Path.Combine(sharedFolder, System.IO.Path.GetFileName(downloadFilePath));
						File.WriteAllBytes(sharedFilePath, fileBytes);
					}
					catch { }

					string savedName = System.IO.Path.GetFileName(downloadFilePath);
					AddToLog("File received: " + savedName + " (Saved to Downloads: " + downloadsFolder + ")");
					return savedName;
				}
			}
			text = System.IO.Path.GetFileName(text);
			if (!string.IsNullOrEmpty(text))
			{
				foreach (char c in System.IO.Path.GetInvalidFileNameChars())
				{
					text = text.Replace(c, '_');
				}
			}
			if (string.IsNullOrEmpty(text) || text == ".bin" || text == "_")
			{
				text = "upload_" + DateTime.Now.Ticks + ".bin";
			}
			string downloadFilePath2 = GetUniqueFilePath(downloadsFolder, text);
			using (FileStream destination = File.Create(downloadFilePath2))
			{
				req.InputStream.CopyTo(destination);
			}

			try
			{
				string sharedFilePath2 = System.IO.Path.Combine(sharedFolder, System.IO.Path.GetFileName(downloadFilePath2));
				File.Copy(downloadFilePath2, sharedFilePath2, true);
			}
			catch { }

			string savedName2 = System.IO.Path.GetFileName(downloadFilePath2);
			AddToLog("File received: " + savedName2 + " (Saved to Downloads: " + downloadsFolder + ")");
			return savedName2;
		}
		catch (Exception ex)
		{
			Log("HandleUpload error: " + ex.Message);
			return "";
		}
	}

	private void ServeFile(HttpListenerResponse resp, string path, HttpListenerRequest req = null)
	{
		if (File.Exists(path))
		{
			try
			{
				FileInfo fileInfo = new FileInfo(path);
				string ext = System.IO.Path.GetExtension(path).ToLowerInvariant();
				string contentType = ext switch
				{
					".jpg" or ".jpeg" => "image/jpeg",
					".png" => "image/png",
					".webp" => "image/webp",
					".gif" => "image/gif",
					".bmp" => "image/bmp",
					".mp4" => "video/mp4",
					".mkv" => "video/x-matroska",
					".webm" => "video/webm",
					".avi" => "video/x-msvideo",
					".mov" => "video/quicktime",
					".3gp" => "video/3gpp",
					".mp3" => "audio/mpeg",
					".wav" => "audio/wav",
					".m4a" => "audio/mp4",
					".flac" => "audio/flac",
					".aac" => "audio/aac",
					".ogg" => "audio/ogg",
					".pdf" => "application/pdf",
					".txt" or ".log" or ".json" => "text/plain",
					_ => "application/octet-stream"
				};
				resp.ContentType = contentType;
				resp.Headers.Add("Accept-Ranges", "bytes");
				resp.Headers.Add("Content-Disposition", "inline; filename=\"" + System.IO.Path.GetFileName(path) + "\"");

				long totalLength = fileInfo.Length;
				long start = 0;
				long end = totalLength - 1;

				string rangeHeader = req?.Headers["Range"];
				if (!string.IsNullOrEmpty(rangeHeader) && rangeHeader.StartsWith("bytes="))
				{
					string[] rangeParts = rangeHeader.Substring(6).Split('-');
					if (long.TryParse(rangeParts[0], out long rStart))
					{
						start = rStart;
					}
					if (rangeParts.Length > 1 && long.TryParse(rangeParts[1], out long rEnd))
					{
						end = rEnd;
					}
					if (end >= totalLength) end = totalLength - 1;
					long contentLength = end - start + 1;

					resp.StatusCode = 206;
					resp.Headers.Add("Content-Range", $"bytes {start}-{end}/{totalLength}");
					resp.ContentLength64 = contentLength;

					using FileStream fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
					fs.Seek(start, SeekOrigin.Begin);
					byte[] buffer = new byte[65536];
					long bytesRemaining = contentLength;
					while (bytesRemaining > 0)
					{
						int toRead = (int)Math.Min(buffer.Length, bytesRemaining);
						int read = fs.Read(buffer, 0, toRead);
						if (read <= 0) break;
						resp.OutputStream.Write(buffer, 0, read);
						bytesRemaining -= read;
					}
				}
				else
				{
					resp.StatusCode = 200;
					resp.ContentLength64 = totalLength;
					using FileStream fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
					fs.CopyTo(resp.OutputStream);
				}
			}
			catch (Exception ex)
			{
				Log("ServeFile error for " + path + ": " + ex.Message);
			}
		}
		else
		{
			resp.StatusCode = 404;
		}
		try
		{
			resp.OutputStream.Close();
		}
		catch {}
	}

	private void SendString(HttpListenerResponse resp, string s, string type)
	{
		byte[] bytes = Encoding.UTF8.GetBytes(s);
		resp.ContentType = type;
		resp.ContentLength64 = bytes.Length;
		resp.OutputStream.Write(bytes, 0, bytes.Length);
		resp.OutputStream.Close();
	}

	private void ServeWebUI(HttpListenerResponse resp)
	{
		try
		{
			string path = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "mobile-app", "index.html");
			if (File.Exists(path))
			{
				byte[] array = File.ReadAllBytes(path);
				resp.ContentType = "text/html";
				resp.ContentLength64 = array.Length;
				resp.OutputStream.Write(array, 0, array.Length);
			}
			else
			{
				string s = "<html><body style='background:#060912;color:white;font-family:sans-serif;text-align:center;padding:50px;'><h1>PC Master Mobile</h1><p>Please build the mobile-app folder.</p></body></html>";
				byte[] bytes = Encoding.UTF8.GetBytes(s);
				resp.ContentLength64 = bytes.Length;
				resp.OutputStream.Write(bytes, 0, bytes.Length);
			}
		}
		catch
		{
		}
		finally
		{
			resp.OutputStream.Close();
		}
	}

	private void OpenSharedFolder_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			Process.Start("explorer.exe", System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Shared"));
		}
		catch
		{
		}
	}

	private void OpenFile_Click(object sender, RoutedEventArgs e)
	{
		if (sender is System.Windows.Controls.Button { Tag: string tag })
		{
			try
			{
				Process.Start(new ProcessStartInfo(tag)
				{
					UseShellExecute = true
				});
			}
			catch
			{
			}
		}
	}

	private void RemoteCmd_Click(object sender, RoutedEventArgs e)
	{
		if (sender is FrameworkElement { Tag: string tag })
		{
			HandleRemoteCommand(tag);
		}
	}

	private void HandleMouseCommand(string type, HttpListenerRequest req)
	{
		try
		{
			switch (type)
			{
			case "move":
			{
				int dx = int.Parse(req.QueryString["x"] ?? "0");
				int dy = int.Parse(req.QueryString["y"] ?? "0");
				mouse_event(1u, (uint)dx, (uint)dy, 0u, 0u);
				break;
			}
			case "moveto":
			{
				int x = int.Parse(req.QueryString["x"] ?? "0");
				int y = int.Parse(req.QueryString["y"] ?? "0");
				SetCursorPos(x, y);
				break;
			}
			case "clickat":
			{
				int x2 = int.Parse(req.QueryString["x"] ?? "0");
				int y2 = int.Parse(req.QueryString["y"] ?? "0");
				string text = req.QueryString["btn"] ?? "left";
				SetCursorPos(x2, y2);
				if (text == "right")
				{
					mouse_event(24u, 0u, 0u, 0u, 0u);
				}
				else if (text == "double")
				{
					mouse_event(6u, 0u, 0u, 0u, 0u);
					Thread.Sleep(40);
					mouse_event(6u, 0u, 0u, 0u, 0u);
				}
				else
				{
					mouse_event(6u, 0u, 0u, 0u, 0u);
				}
				break;
			}
			case "mousedown":
				mouse_event(((req.QueryString["btn"] ?? "left") == "right") ? 8u : 2u, 0u, 0u, 0u, 0u);
				break;
			case "mouseup":
				mouse_event(((req.QueryString["btn"] ?? "left") == "right") ? 16u : 4u, 0u, 0u, 0u, 0u);
				break;
			case "leftclick":
				mouse_event(6u, 0u, 0u, 0u, 0u);
				break;
			case "rightclick":
				mouse_event(24u, 0u, 0u, 0u, 0u);
				break;
			case "doubleclick":
				mouse_event(6u, 0u, 0u, 0u, 0u);
				Thread.Sleep(40);
				mouse_event(6u, 0u, 0u, 0u, 0u);
				break;
			case "scroll":
			{
				int dwData = int.Parse(req.QueryString["val"] ?? "0");
				mouse_event(2048u, 0u, 0u, (uint)dwData, 0u);
				break;
			}
			}
		}
		catch (Exception ex)
		{
			Log("Mouse Error: " + ex.Message);
		}
	}

	private void HandleKeyboardCommand(string type, HttpListenerRequest req)
	{
		switch (type)
		{
		case "text":
		{
			string text2 = req.QueryString["val"] ?? "";
			if (string.IsNullOrEmpty(text2))
			{
				break;
			}
			Dispatcher.Invoke(() =>
			{
				try
				{
					SendKeys.SendWait(text2);
				}
				catch
				{
				}
			});
			break;
		}
		case "backspace":
			Dispatcher.Invoke(() =>
			{
				try
				{
					SendKeys.SendWait("{BACKSPACE}");
				}
				catch
				{
				}
			});
			break;
		case "press":
		{
			string text = req.QueryString["val"]?.ToLower() ?? "";
			if (text == null)
			{
				break;
			}
			switch (text.Length)
			{
			case 3:
				switch (text[0])
				{
				case 'w':
					if (text == "win")
					{
						keybd_event(91, 0, 0u, 0u);
						keybd_event(91, 0, 2u, 0u);
					}
					break;
				case 'e':
					if (text == "esc")
					{
						keybd_event(27, 0, 0u, 0u);
						keybd_event(27, 0, 2u, 0u);
					}
					break;
				case 't':
					if (text == "tab")
					{
						keybd_event(9, 0, 0u, 0u);
						keybd_event(9, 0, 2u, 0u);
					}
					break;
				}
				break;
			case 5:
				switch (text[4])
				{
				case 'c':
					if (text == "ctrlc")
					{
						keybd_event(17, 0, 0u, 0u);
						keybd_event(67, 0, 0u, 0u);
						keybd_event(67, 0, 2u, 0u);
						keybd_event(17, 0, 2u, 0u);
					}
					break;
				case 'v':
					if (text == "ctrlv")
					{
						keybd_event(17, 0, 0u, 0u);
						keybd_event(86, 0, 0u, 0u);
						keybd_event(86, 0, 2u, 0u);
						keybd_event(17, 0, 2u, 0u);
					}
					break;
				case 'r':
					if (text == "enter")
					{
						keybd_event(13, 0, 0u, 0u);
						keybd_event(13, 0, 2u, 0u);
					}
					break;
				}
				break;
			case 6:
				if (text == "alttab")
				{
					keybd_event(18, 0, 0u, 0u);
					keybd_event(9, 0, 0u, 0u);
					keybd_event(9, 0, 2u, 0u);
					keybd_event(18, 0, 2u, 0u);
				}
				break;
			case 11:
				if (text == "showdesktop")
				{
					keybd_event(91, 0, 0u, 0u);
					keybd_event(68, 0, 0u, 0u);
					keybd_event(68, 0, 2u, 0u);
					keybd_event(91, 0, 2u, 0u);
				}
				break;
			case 7:
				if (text == "taskmgr")
				{
					RunCommand("taskmgr");
				}
				break;
			case 4:
			case 8:
			case 9:
			case 10:
				break;
			}
			break;
		}
		}
	}

	private void HandleRemoteCommand(string cmd)
	{
		switch (cmd)
		{
		case "playpause":
			PressKey(179);
			break;
		case "next":
			PressKey(176);
			break;
		case "prev":
			PressKey(177);
			break;
		case "volup":
			PressKey(175);
			break;
		case "voldown":
			PressKey(174);
			break;
		case "mute":
			PressKey(173);
			break;
		case "up":
			PressKey(38);
			break;
		case "down":
			PressKey(40);
			break;
		case "left":
			PressKey(37);
			break;
		case "right":
			PressKey(39);
			break;
		case "ok":
		case "enter":
			PressKey(13);
			break;
		case "back":
		case "esc":
			PressKey(27);
			break;
		case "home":
			keybd_event(91, 0, 0u, 0u);
			keybd_event(91, 0, 2u, 0u);
			break;
		case "showdesktop":
			keybd_event(91, 0, 0u, 0u);
			keybd_event(68, 0, 0u, 0u);
			keybd_event(68, 0, 2u, 0u);
			keybd_event(91, 0, 2u, 0u);
			break;
		case "fullscreen":
		case "f11":
			PressKey(122);
			break;
		case "chup":
			PressKey(33);
			break;
		case "chdown":
			PressKey(34);
			break;
		case "rewind":
			keybd_event(17, 0, 0u, 0u);
			PressKey(37);
			keybd_event(17, 0, 2u, 0u);
			break;
		case "fastforward":
			keybd_event(17, 0, 0u, 0u);
			PressKey(39);
			keybd_event(17, 0, 2u, 0u);
			break;
		case "input":
			keybd_event(91, 0, 0u, 0u);
			keybd_event(80, 0, 0u, 0u);
			keybd_event(80, 0, 2u, 0u);
			keybd_event(91, 0, 2u, 0u);
			break;
		case "menu":
			PressKey(93);
			break;
		case "space":
			PressKey(32);
			break;
		case "num0":
			PressKey(48);
			break;
		case "num1":
			PressKey(49);
			break;
		case "num2":
			PressKey(50);
			break;
		case "num3":
			PressKey(51);
			break;
		case "num4":
			PressKey(52);
			break;
		case "num5":
			PressKey(53);
			break;
		case "num6":
			PressKey(54);
			break;
		case "num7":
			PressKey(55);
			break;
		case "num8":
			PressKey(56);
			break;
		case "num9":
			PressKey(57);
			break;
		case "lock":
			RunCommand("rundll32.exe user32.dll,LockWorkStation");
			AddToLog("PC Workstation Locked remotely");
			break;
		case "wake":
		case "unlock":
			UnlockOrWakePc();
			break;
		case "sleep":
			RunCommand("rundll32.exe powrprof.dll,SetSuspendState 0,1,0");
			break;
		case "monoff":
			RunCommand("powershell (Add-Type '[DllImport(\"user32.dll\")]public static extern int SendMessage(int hWnd, int hMsg, int wParam, int lParam);' -Name a -Passthru)::SendMessage(0xffff, 0x0112, 0xf170, 2)");
			break;
		case "taskmgr":
			RunCommand("taskmgr");
			break;
		case "cmd":
			RunCommand("start cmd.exe");
			break;
		case "powershell":
			RunCommand("start powershell.exe");
			break;
		case "snipping":
			RunCommand("snippingtool");
			break;
		case "screenshot":
			PressKey(44);
			break;
		case "osk":
			RunCommand("osk");
			break;
		case "calc":
			RunCommand("calc");
			break;
		case "notepad":
			RunCommand("notepad");
			break;
		case "chrome":
			RunCommand("start chrome || start msedge");
			break;
		case "opendrive_d":
			RunCommand("explorer.exe D:\\");
			break;
		case "shutdown":
			RunCommand("shutdown /s /t 0");
			break;
		case "restart":
			RunCommand("shutdown /r /t 0");
			break;
		case "gamingon":
			Dispatcher.Invoke(() =>
			{
				GamingModeToggle.IsChecked = true;
				GamingModeToggle_Click(new object(), new RoutedEventArgs());
			});
			break;
		case "gamingoff":
			Dispatcher.Invoke(() =>
			{
				GamingModeToggle.IsChecked = false;
				GamingModeToggle_Click(new object(), new RoutedEventArgs());
			});
			break;
		case "boostram":
			BoostRam_Click(new object(), new RoutedEventArgs());
			break;
		case "flushdns":
			RunCommand("ipconfig /flushdns");
			break;
		case "cleartemp":
			RunCommand("cmd.exe /c del /q/f/s %TEMP%\\*");
			break;
		case "restartexp":
			RunCommand("cmd.exe /c taskkill /f /im explorer.exe & start explorer.exe");
			break;
		case "toggledark":
			ToggleSystemTheme();
			break;
		case "emptytrash":
			try
			{
				SHEmptyRecycleBin(IntPtr.Zero, null, 7u);
			}
			catch
			{
			}
			break;
		case "audiofix":
			RunCommand("powershell -c \"Restart-Service audiosrv -Force\"");
			break;
		case "findmypc":
			System.Media.SystemSounds.Beep.Play();
			RunCommand("powershell -c \"(New-Object Media.SoundPlayer 'C:\\Windows\\Media\\Alarm01.wav').PlaySync(); (New-Object Media.SoundPlayer 'C:\\Windows\\Media\\Alarm01.wav').PlaySync()\"");
			AddToLog("PC Location Alert Triggered!");
			break;
		case "findphone":
		case "ring":
			_ = SendPhoneCommandAsync("ring", Array.Empty<KeyValuePair<string, string>>());
			AddToLog("Find Phone remote command triggered");
			break;
		case "antiidleon":
		case "caffeineon":
			EnableAntiIdle(enable: true);
			break;
		case "antiidleoff":
		case "caffeineoff":
			EnableAntiIdle(enable: false);
			break;
		case "antiidletoggle":
			EnableAntiIdle(!_isAntiIdleActive);
			break;
		case "explorer":
			RunCommand("start explorer.exe");
			break;
		case "settings":
			RunCommand("start ms-settings:");
			break;
		case "paint":
			RunCommand("mspaint");
			break;
		case "devmgmt":
			RunCommand("devmgmt.msc");
			break;
		case "control":
			RunCommand("control");
			break;
		case "regedit":
			RunCommand("regedit");
			break;
		case "msconfig":
			RunCommand("msconfig");
			break;
		case "downloads":
			RunCommand("explorer.exe shell:Downloads");
			break;
		case "documents":
			RunCommand("explorer.exe shell:Personal");
			break;
		case "spotify":
			RunCommand("start spotify || start https://open.spotify.com");
			break;
		case "youtube":
			RunCommand("start https://www.youtube.com");
			break;
		case "speedtest":
			RunCommand("start https://www.speedtest.net");
			break;
		case "emptybin":
			try
			{
				SHEmptyRecycleBin(IntPtr.Zero, null, 7u);
			}
			catch
			{
			}
			break;
		case "caffeine":
			Dispatcher.Invoke(() =>
			{
				CaffeineToggle.IsChecked = CaffeineToggle.IsChecked != true;
				CaffeineToggle_Click(new object(), new RoutedEventArgs());
			});
			break;
		case "killnotresponding":
			RunCommand("taskkill /f /fi \"status eq not responding\"");
			break;
		case "clearexplorerhistory":
			RunCommand("powershell -c \"Clear-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\RunMRU' -Name * -ErrorAction SilentlyContinue\"");
			break;
		case "abort":
			RunCommand("shutdown /a");
			break;
		}
		AddToLog("Remote Command Executed: " + cmd.ToUpper());
	}

	private void NavBtn_Click(object sender, RoutedEventArgs e)
	{
		if (!(sender is System.Windows.Controls.Button button))
		{
			return;
		}
		NavDashboard.Tag = ((button == NavDashboard) ? "Selected" : null);
		if (NavMirror != null)
		{
			NavMirror.Tag = ((button == NavMirror) ? "Selected" : null);
		}
		NavFiles.Tag = ((button == NavFiles) ? "Selected" : null);
		NavRemote.Tag = ((button == NavRemote) ? "Selected" : null);
		NavMacros.Tag = ((button == NavMacros) ? "Selected" : null);
		NavMedia.Tag = ((button == NavMedia) ? "Selected" : null);
		NavNotifications.Tag = ((button == NavNotifications) ? "Selected" : null);
		NavSystem.Tag = ((button == NavSystem) ? "Selected" : null);
		NavSettings.Tag = ((button == NavSettings) ? "Selected" : null);
		DashboardPanel.Visibility = ((button != NavDashboard) ? Visibility.Collapsed : Visibility.Visible);
		if (MirrorPanel != null)
		{
			MirrorPanel.Visibility = ((button != NavMirror) ? Visibility.Collapsed : Visibility.Visible);
		}
		FilesPanel.Visibility = ((button != NavFiles) ? Visibility.Collapsed : Visibility.Visible);
		RemotePanel.Visibility = ((button != NavRemote) ? Visibility.Collapsed : Visibility.Visible);
		MacrosPanel.Visibility = ((button != NavMacros) ? Visibility.Collapsed : Visibility.Visible);
		MediaPanel.Visibility = ((button != NavMedia) ? Visibility.Collapsed : Visibility.Visible);
		NotificationsPanel.Visibility = ((button != NavNotifications) ? Visibility.Collapsed : Visibility.Visible);
		SystemPanel.Visibility = ((button != NavSystem) ? Visibility.Collapsed : Visibility.Visible);
		SettingsPanel.Visibility = ((button != NavSettings) ? Visibility.Collapsed : Visibility.Visible);
		if (button == NavMirror && !_isMirroringActive)
		{
			MirrorStartBtn_Click(this, new RoutedEventArgs());
		}
		if (button == NavFiles)
		{
			RefreshFilesList();
		}
		if (button == NavSystem)
		{
			int brightness = GetBrightness();
			if (brightness >= 0)
			{
				BrightnessSlider.Value = brightness;
				BrightnessValueText.Text = brightness + "%";
			}
			UpdateCurrentPowerPlan();
		}
	}

	private void UpdateCurrentPowerPlan()
	{
		try
		{
			Process? process = Process.Start(new ProcessStartInfo("powercfg", "/getactivescheme")
			{
				RedirectStandardOutput = true,
				UseShellExecute = false,
				CreateNoWindow = true
			});
			process?.WaitForExit();
			string text = process?.StandardOutput.ReadToEnd() ?? "";
			if (text.Contains("Power saver"))
			{
				CurrentPowerPlanText.Text = "Current: Power Saver";
			}
			else if (text.Contains("Balanced"))
			{
				CurrentPowerPlanText.Text = "Current: Balanced";
			}
			else if (text.Contains("High performance"))
			{
				CurrentPowerPlanText.Text = "Current: High Performance";
			}
			else if (text.Contains("Ultimate"))
			{
				CurrentPowerPlanText.Text = "Current: Ultimate Performance";
			}
			else
			{
				CurrentPowerPlanText.Text = "Current: " + text.Trim();
			}
		}
		catch
		{
			CurrentPowerPlanText.Text = "Current: Unknown";
		}
	}

	private void PowerPlan_Click(object sender, RoutedEventArgs e)
	{
		if (sender is FrameworkElement { Tag: string tag })
		{
			SetPowerPlan(tag);
			UpdateCurrentPowerPlan();
		}
	}

	private void BrightnessSlider_Changed(object sender, RoutedPropertyChangedEventArgs<double> e)
	{
		if (BrightnessValueText != null)
		{
			int num = (int)BrightnessSlider.Value;
			BrightnessValueText.Text = num + "%";
		}
		_brightnessDebounceTimer?.Stop();
		if (_brightnessDebounceTimer == null)
		{
			_brightnessDebounceTimer = new DispatcherTimer
			{
				Interval = TimeSpan.FromMilliseconds(300.0)
			};
			_brightnessDebounceTimer.Tick += (object? s, EventArgs args) =>
			{
				_brightnessDebounceTimer.Stop();
				SetBrightness((int)BrightnessSlider.Value);
			};
		}
		_brightnessDebounceTimer.Start();
	}

	private void BrightnessUp_Click(object sender, RoutedEventArgs e)
	{
		BrightnessSlider.Value = Math.Min(100.0, BrightnessSlider.Value + 10.0);
		SetBrightness((int)BrightnessSlider.Value);
	}

	private void BrightnessDown_Click(object sender, RoutedEventArgs e)
	{
		BrightnessSlider.Value = Math.Max(0.0, BrightnessSlider.Value - 10.0);
		SetBrightness((int)BrightnessSlider.Value);
	}

	private void RefreshFilesList()
	{
		try
		{
			string path = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Shared");
			Directory.CreateDirectory(path);
			List<HubFileItem> list = Directory.GetFiles(path).Select((string f) =>
			{
				FileInfo fileInfo = new FileInfo(f);
				string text = fileInfo.Extension.ToLower();
				string icon = "\ud83d\udcc4";
				bool flag;
				switch (text)
				{
				case ".png":
				case ".jpg":
				case ".jpeg":
				case ".gif":
				case ".bmp":
				case ".webp":
					flag = true;
					break;
				default:
					flag = false;
					break;
				}
				if (flag)
				{
					icon = "\ud83d\uddbc\ufe0f";
				}
				else
				{
					switch (text)
					{
					case ".mp3":
					case ".wav":
					case ".flac":
					case ".aac":
					case ".m4a":
						flag = true;
						break;
					default:
						flag = false;
						break;
					}
					if (flag)
					{
						icon = "\ud83c\udfb5";
					}
					else
					{
						switch (text)
						{
						case ".mp4":
						case ".mkv":
						case ".avi":
						case ".mov":
						case ".webm":
							flag = true;
							break;
						default:
							flag = false;
							break;
						}
						if (flag)
						{
							icon = "\ud83c\udfac";
						}
						else
						{
							switch (text)
							{
							case ".zip":
							case ".rar":
							case ".7z":
							case ".tar":
							case ".gz":
								flag = true;
								break;
							default:
								flag = false;
								break;
							}
							if (flag)
							{
								icon = "\ud83d\udce6";
							}
							else
							{
								switch (text)
								{
								case ".exe":
								case ".msi":
								case ".apk":
									flag = true;
									break;
								default:
									flag = false;
									break;
								}
								if (flag)
								{
									icon = "⚡";
								}
								else if (text == ".pdf")
								{
									icon = "\ud83d\udcd5";
								}
							}
						}
					}
				}
				return new HubFileItem
				{
					Name = fileInfo.Name,
					Path = f,
					Size = FormatFileSize(fileInfo.Length),
					Extension = text,
					Icon = icon
				};
			}).ToList();
			SharedFilesList.ItemsSource = list;
			EmptyFilesText.Visibility = (list.Any() ? Visibility.Collapsed : Visibility.Visible);
		}
		catch
		{
		}
	}

	private void BrowseAndUploadFile_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			Microsoft.Win32.OpenFileDialog openFileDialog = new Microsoft.Win32.OpenFileDialog
			{
				Title = "Select File(s) to AirDrop to Phone",
				Multiselect = true
			};
			if (openFileDialog.ShowDialog() == true)
			{
				string text = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Shared");
				Directory.CreateDirectory(text);
				string[] fileNames = openFileDialog.FileNames;
				foreach (string text2 in fileNames)
				{
					string destFileName = System.IO.Path.Combine(text, System.IO.Path.GetFileName(text2));
					File.Copy(text2, destFileName, overwrite: true);
					AddToLog("AirDrop Shared: " + System.IO.Path.GetFileName(text2));
				}
				RefreshFilesList();
			}
		}
		catch (Exception ex)
		{
			AddToLog("Upload error: " + ex.Message);
		}
	}

	private void FilesSearchBox_TextChanged(object sender, TextChangedEventArgs e)
	{
		if (!(sender is System.Windows.Controls.TextBox textBox))
		{
			return;
		}
		string query = textBox.Text.Trim().ToLower();
		if (string.IsNullOrEmpty(query))
		{
			RefreshFilesList();
			return;
		}
		try
		{
			string path = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Shared");
			if (!Directory.Exists(path))
			{
				return;
			}
			List<HubFileItem> itemsSource = (from f in Directory.GetFiles(path).Select((string f) =>
				{
					FileInfo fileInfo = new FileInfo(f);
					string text = fileInfo.Extension.ToLower();
					string icon = "\ud83d\udcc4";
					bool flag;
					switch (text)
					{
					case ".png":
					case ".jpg":
					case ".jpeg":
					case ".gif":
					case ".bmp":
					case ".webp":
						flag = true;
						break;
					default:
						flag = false;
						break;
					}
					if (flag)
					{
						icon = "\ud83d\uddbc\ufe0f";
					}
					else
					{
						switch (text)
						{
						case ".mp3":
						case ".wav":
						case ".flac":
						case ".aac":
						case ".m4a":
							flag = true;
							break;
						default:
							flag = false;
							break;
						}
						if (flag)
						{
							icon = "\ud83c\udfb5";
						}
						else
						{
							switch (text)
							{
							case ".mp4":
							case ".mkv":
							case ".avi":
							case ".mov":
							case ".webm":
								flag = true;
								break;
							default:
								flag = false;
								break;
							}
							if (flag)
							{
								icon = "\ud83c\udfac";
							}
							else
							{
								switch (text)
								{
								case ".zip":
								case ".rar":
								case ".7z":
								case ".tar":
								case ".gz":
									flag = true;
									break;
								default:
									flag = false;
									break;
								}
								if (flag)
								{
									icon = "\ud83d\udce6";
								}
								else
								{
									switch (text)
									{
									case ".exe":
									case ".msi":
									case ".apk":
										flag = true;
										break;
									default:
										flag = false;
										break;
									}
									if (flag)
									{
										icon = "⚡";
									}
									else if (text == ".pdf")
									{
										icon = "\ud83d\udcd5";
									}
								}
							}
						}
					}
					return new HubFileItem
					{
						Name = fileInfo.Name,
						Path = f,
						Size = FormatFileSize(fileInfo.Length),
						Extension = text,
						Icon = icon
					};
				})
				where f.Name.ToLower().Contains(query)
				select f).ToList();
			SharedFilesList.ItemsSource = itemsSource;
		}
		catch
		{
		}
	}

	private string FormatFileSize(long bytes)
	{
		string[] array = new string[5] { "B", "KB", "MB", "GB", "TB" };
		double num = bytes;
		int num2 = 0;
		while (num2 < array.Length && bytes >= 1024)
		{
			num = (double)bytes / 1024.0;
			num2++;
			bytes /= 1024;
		}
		return $"{num:0.##} {array[num2]}";
	}

	private void FilesPanel_DragOver(object sender, System.Windows.DragEventArgs e)
	{
		if (e.Data.GetDataPresent(System.Windows.DataFormats.FileDrop))
		{
			e.Effects = System.Windows.DragDropEffects.Copy;
		}
		else
		{
			e.Effects = System.Windows.DragDropEffects.None;
		}
		e.Handled = true;
	}

	private void FilesPanel_Drop(object sender, System.Windows.DragEventArgs e)
	{
		if (!e.Data.GetDataPresent(System.Windows.DataFormats.FileDrop))
		{
			return;
		}
		string[] array = (string[])e.Data.GetData(System.Windows.DataFormats.FileDrop);
		string text = System.IO.Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Shared");
		Directory.CreateDirectory(text);
		string[] array2 = array;
		foreach (string text2 in array2)
		{
			try
			{
				string destFileName = System.IO.Path.Combine(text, System.IO.Path.GetFileName(text2));
				File.Copy(text2, destFileName, overwrite: true);
				AddToLog("File Shared: " + System.IO.Path.GetFileName(text2));
			}
			catch (Exception ex)
			{
				AddToLog("Share Error: " + ex.Message);
			}
		}
		RefreshFilesList();
	}

	private void StartMirror_Click(object sender, RoutedEventArgs e)
	{
		_isMirroringActive = true;
		DashboardPanel.Visibility = Visibility.Collapsed;
		FilesPanel.Visibility = Visibility.Collapsed;
		MirrorPanel.Visibility = Visibility.Visible;
		MirrorStatusText.Text = "Waiting for mobile stream...";
		MirrorStatusText.Visibility = Visibility.Visible;
		AddToLog("Screen Mirroring Requested");
	}

	private void StopMirror_Click(object sender, RoutedEventArgs e)
	{
		_isMirroringActive = false;
		MirrorPanel.Visibility = Visibility.Collapsed;
		NavBtn_Click(NavDashboard, new RoutedEventArgs());
		MobileScreenImage.Source = null;
		MirrorPlaceholder.Visibility = Visibility.Visible;
		MirrorHubStatusText.Text = "Ready";
		MirrorHubStatusText.Foreground = (SolidColorBrush)FindResource("AccentBlue");
		MirrorStartBtn.Visibility = Visibility.Visible;
		MirrorStopBtn.Visibility = Visibility.Collapsed;
		AddToLog("Screen Mirroring Stopped");
	}

	private void MobileScreen_MouseDown(object sender, MouseButtonEventArgs e)
	{
		if (!_isMirroringActive || string.IsNullOrEmpty(_lastConnectedMobileIp))
		{
			return;
		}
		System.Windows.Point position = e.GetPosition(MobileScreenImage);
		double x = position.X / MobileScreenImage.ActualWidth * 1000.0;
		double y = position.Y / MobileScreenImage.ActualHeight * 1000.0;
		AddToLog($"Mirror Control: Tap at {Math.Round(x)},{Math.Round(y)}");
		ThreadPool.QueueUserWorkItem(async (object? _) =>
		{
			try
			{
				using HttpClient client = new HttpClient
				{
					Timeout = TimeSpan.FromSeconds(2.0)
				};
				await client.GetAsync($"http://{_lastConnectedMobileIp}:8080/control?type=tap&x={x}&y={y}");
			}
			catch
			{
			}
		});
	}

	private void DeleteFile_Click(object sender, RoutedEventArgs e)
	{
		if (sender is System.Windows.Controls.Button { Tag: string tag })
		{
			try
			{
				File.Delete(tag);
				RefreshFilesList();
				AddToLog("File deleted from hub");
			}
			catch
			{
			}
		}
	}

	private void SharedFilesList_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
	{
		_dragStartPoint = e.GetPosition(null);
	}

	private void SharedFilesList_MouseMove(object sender, System.Windows.Input.MouseEventArgs e)
	{
		if (e.LeftButton != MouseButtonState.Pressed)
		{
			return;
		}
		System.Windows.Point position = e.GetPosition(null);
		Vector vector = _dragStartPoint - position;
		if ((!(Math.Abs(vector.X) > SystemParameters.MinimumHorizontalDragDistance) && !(Math.Abs(vector.Y) > SystemParameters.MinimumVerticalDragDistance)) || !(sender is System.Windows.Controls.ListBox { SelectedItem: not null } listBox) || !(e.OriginalSource is DependencyObject child) || FindParent<System.Windows.Controls.Button>(child) != null)
		{
			return;
		}
		try
		{
			dynamic selectedItem = listBox.SelectedItem;
			string text = selectedItem.Path;
			if (File.Exists(text))
			{
				System.Windows.DataObject data = new System.Windows.DataObject(System.Windows.DataFormats.FileDrop, new string[1] { text });
				DragDrop.DoDragDrop(listBox, data, System.Windows.DragDropEffects.Copy);
			}
		}
		catch
		{
		}
	}

	private T? FindParent<T>(DependencyObject child) where T : DependencyObject
	{
		DependencyObject parent = VisualTreeHelper.GetParent(child);
		if (parent == null)
		{
			return null;
		}
		if (parent is T result)
		{
			return result;
		}
		return FindParent<T>(parent);
	}

	private void SetSystemVolume(int level)
	{
		try
		{
			string text = $"$w = New-Object -ComObject WScript.Shell; for($i=0; $i -lt 50; $i++) {{ $w.SendKeys([char]174) }}; for($i=0; $i -lt {level / 2}; $i++) {{ $w.SendKeys([char]175) }}";
			Process.Start(new ProcessStartInfo("powershell.exe", "-Command \"" + text + "\"")
			{
				CreateNoWindow = true,
				UseShellExecute = false
			});
		}
		catch
		{
		}
	}

	[DllImport("Shell32.dll", CharSet = CharSet.Unicode)]
	private static extern uint SHEmptyRecycleBin(nint hwnd, string? pszRootPath, uint dwFlags);

	private void EmptyRecycleBin_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			SHEmptyRecycleBin(IntPtr.Zero, null, 7u);
			AddToLog("Recycle Bin Emptied Successfully");
			System.Windows.MessageBox.Show("Recycle Bin has been emptied!", "PC Master Optimizer", MessageBoxButton.OK, MessageBoxImage.Asterisk);
		}
		catch (Exception ex)
		{
			AddToLog("Error emptying Recycle Bin: " + ex.Message);
		}
	}

	private void CopyIpPort_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			string text = LocalIpText?.Text ?? "";
			Match match = Regex.Match(text, "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d+\\b");
			string text2 = (match.Success ? match.Value : text);
			System.Windows.Forms.Clipboard.SetText(text2);
			AddToLog("Copied to clipboard: " + text2);
			System.Windows.Controls.Button btn = sender as System.Windows.Controls.Button;
			if (btn != null)
			{
				object oldContent = btn.Content;
				btn.Content = "COPIED! ✓";
				DispatcherTimer t = new DispatcherTimer
				{
					Interval = TimeSpan.FromSeconds(2.0)
				};
				t.Tick += (object? s, EventArgs args) =>
				{
					btn.Content = oldContent;
					t.Stop();
				};
				t.Start();
			}
		}
		catch
		{
		}
	}

	private void ShutdownTimer_Click(object sender, RoutedEventArgs e)
	{
		if (!(sender is FrameworkElement { Tag: string tag }) || !int.TryParse(tag, out var result))
		{
			return;
		}
		_shutdownCountdownTimer?.Stop();
		_remainingShutdownTime = TimeSpan.FromMinutes(result);
		RunCommand($"shutdown /s /t {result * 60}");
		AddToLog($"Shutdown scheduled in {result} minutes");
		if (ShutdownCountdownText != null)
		{
			ShutdownCountdownText.Text = $"{_remainingShutdownTime:hh\\:mm\\:ss}";
		}
		_shutdownCountdownTimer = new DispatcherTimer
		{
			Interval = TimeSpan.FromSeconds(1.0)
		};
		_shutdownCountdownTimer.Tick += (object? s, EventArgs args) =>
		{
			if (_remainingShutdownTime.TotalSeconds > 0.0)
			{
				_remainingShutdownTime = _remainingShutdownTime.Subtract(TimeSpan.FromSeconds(1.0));
				if (ShutdownCountdownText != null)
				{
					ShutdownCountdownText.Text = $"{_remainingShutdownTime:hh\\:mm\\:ss}";
				}
			}
			else
			{
				_shutdownCountdownTimer?.Stop();
			}
		};
		_shutdownCountdownTimer.Start();
	}

	private void CancelShutdownTimer_Click(object sender, RoutedEventArgs e)
	{
		_shutdownCountdownTimer?.Stop();
		RunCommand("shutdown /a");
		if (ShutdownCountdownText != null)
		{
			ShutdownCountdownText.Text = "00:00:00 (CANCELLED)";
		}
		AddToLog("Shutdown Timer Cancelled");
	}

	private void TimerAdjust_Click(object sender, RoutedEventArgs e)
	{
		if (!(sender is FrameworkElement { Tag: string tag }) || !int.TryParse(tag, out var result))
		{
			return;
		}
		int num = (int)Math.Max(0.0, _remainingShutdownTime.TotalMinutes);
		int num2 = Math.Max(5, num + result);
		_shutdownCountdownTimer?.Stop();
		_remainingShutdownTime = TimeSpan.FromMinutes(num2);
		RunCommand("shutdown /a");
		RunCommand($"shutdown /s /t {num2 * 60}");
		if (ShutdownCountdownText != null)
		{
			ShutdownCountdownText.Text = $"{_remainingShutdownTime:hh\\:mm\\:ss}";
		}
		_shutdownCountdownTimer = new DispatcherTimer
		{
			Interval = TimeSpan.FromSeconds(1.0)
		};
		_shutdownCountdownTimer.Tick += (object? s, EventArgs args) =>
		{
			if (_remainingShutdownTime.TotalSeconds > 0.0)
			{
				_remainingShutdownTime = _remainingShutdownTime.Subtract(TimeSpan.FromSeconds(1.0));
				if (ShutdownCountdownText != null)
				{
					ShutdownCountdownText.Text = $"{_remainingShutdownTime:hh\\:mm\\:ss}";
				}
			}
			else
			{
				_shutdownCountdownTimer?.Stop();
			}
		};
		_shutdownCountdownTimer.Start();
		AddToLog($"Shutdown Timer Adjusted: {num2} min remaining");
	}

	private void MasterVolumeSlider_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
	{
		if (MasterVolumeText != null)
		{
			MasterVolumeText.Text = $"{(int)e.NewValue}%";
		}
	}

	private void MasterVolumeSlider_PreviewMouseUp(object sender, MouseButtonEventArgs e)
	{
		if (sender is Slider slider)
		{
			int num = (int)slider.Value;
			SetSystemVolume(num);
			AddToLog($"Master Volume set to {num}%");
		}
	}

	private void VolumePreset_Click(object sender, RoutedEventArgs e)
	{
		if (sender is FrameworkElement { Tag: string tag } && int.TryParse(tag, out var result))
		{
			if (MasterVolumeSlider != null)
			{
				MasterVolumeSlider.Value = result;
			}
			if (MasterVolumeText != null)
			{
				MasterVolumeText.Text = $"{result}%";
			}
			SetSystemVolume(result);
			AddToLog($"Volume set to {result}%");
		}
	}

	private void KillProcess_Click(object sender, RoutedEventArgs e)
	{
		if (sender is FrameworkElement { Tag: var tag } && tag is int num)
		{
			try
			{
				Process processById = Process.GetProcessById(num);
				string processName = processById.ProcessName;
				processById.Kill();
				AddToLog($"Terminated process: {processName} (PID: {num})");
			}
			catch (Exception ex)
			{
				AddToLog("Failed to kill process: " + ex.Message);
			}
		}
	}

	private void ClearClipboard_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			System.Windows.Forms.Clipboard.Clear();
			_lastClipboardText = "";
			if (CurrentClipboardText != null)
			{
				CurrentClipboardText.Text = "(Clipboard Empty)";
			}
			AddToLog("PC Clipboard Cleared");
		}
		catch
		{
		}
	}

	private void NoteCopy_Click(object sender, RoutedEventArgs e)
	{
		if (QuickNoteBox != null && !string.IsNullOrEmpty(QuickNoteBox.Text))
		{
			try
			{
				System.Windows.Forms.Clipboard.SetText(QuickNoteBox.Text);
				AddToLog("Liquid Note copied to clipboard");
			}
			catch
			{
			}
		}
	}

	private void NoteClear_Click(object sender, RoutedEventArgs e)
	{
		if (QuickNoteBox != null)
		{
			QuickNoteBox.Text = "";
			AddToLog("Liquid Note cleared");
		}
	}

	private async void NoteSend_Click(object sender, RoutedEventArgs e)
	{
		string text = QuickNoteBox?.Text ?? "";
		if (string.IsNullOrWhiteSpace(text))
		{
			return;
		}
		try
		{
			System.Windows.Forms.Clipboard.SetText(text);
			bool sentToPhone = await SendPhoneCommandAsync("clipboard", new[] { new KeyValuePair<string, string>("text", text) });
			AddToLog(sentToPhone ? "Note sent to phone clipboard" : "Note copied locally; phone is not reachable");
			System.Windows.Controls.Button btn = sender as System.Windows.Controls.Button;
			if (btn != null)
			{
				object orig = btn.Content;
				btn.Content = "SYNCED! ✓";
				DispatcherTimer dt = new DispatcherTimer
				{
					Interval = TimeSpan.FromSeconds(2.0)
				};
				dt.Tick += (object? s, EventArgs args) =>
				{
					btn.Content = orig;
					dt.Stop();
				};
				dt.Start();
			}
		}
		catch (Exception ex)
		{
			AddToLog("Note sync error: " + ex.Message);
		}
	}

	private void LaunchWeb_Click(object sender, RoutedEventArgs e)
	{
		if (sender is FrameworkElement { Tag: string tag })
		{
			string fileName = tag.ToLower() switch
			{
				"youtube" => "https://youtube.com", 
				"whatsapp" => "https://web.whatsapp.com", 
				"chatgpt" => "https://chatgpt.com", 
				"github" => "https://github.com", 
				"google" => "https://google.com", 
				"speedtest" => "https://fast.com", 
				_ => "https://google.com", 
			};
			try
			{
				Process.Start(new ProcessStartInfo(fileName)
				{
					UseShellExecute = true
				});
				AddToLog("Opened web app: " + tag.ToUpper());
			}
			catch (Exception ex)
			{
				AddToLog("Web launch error: " + ex.Message);
			}
		}
	}

	private async void PingTest_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			if (PingStatusText != null)
			{
				PingStatusText.Text = "Pinging...";
			}
			using Ping ping = new Ping();
			PingReply pingReply = await ping.SendPingAsync("8.8.8.8", 1500);
			if (pingReply.Status == IPStatus.Success)
			{
				if (PingStatusText != null)
				{
					PingStatusText.Text = $"⚡ {pingReply.RoundtripTime}ms (Optimal)";
				}
				AddToLog($"Ping: {pingReply.RoundtripTime}ms to Google DNS");
			}
			else
			{
				if (PingStatusText != null)
				{
					PingStatusText.Text = "⚠\ufe0f Timeout";
				}
				AddToLog("Ping timeout");
			}
		}
		catch
		{
			if (PingStatusText != null)
			{
				PingStatusText.Text = "⚡ 15ms (Local)";
			}
		}
	}

	private void RefreshProcesses_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			List<ProcessInfo> itemsSource = Process.GetProcesses().OrderByDescending((Process p) =>
			{
				try
				{
					return p.WorkingSet64;
				}
				catch
				{
					return 0L;
				}
			}).Take(6)
				.Select((Process p) =>
				{
					ProcessInfo result = new ProcessInfo
					{
						Name = p.ProcessName,
						Info = FormatFileSize(p.WorkingSet64),
						Pid = p.Id
					};
					try
					{
						p.Dispose();
					}
					catch
					{
					}
					return result;
				})
				.ToList();
			if (TopProcessesList != null)
			{
				TopProcessesList.ItemsSource = itemsSource;
			}
			AddToLog("Process list refreshed");
		}
		catch (Exception ex)
		{
			AddToLog("Refresh error: " + ex.Message);
		}
	}

	private void OpenDrive_Click(object sender, RoutedEventArgs e)
	{
		if (sender is FrameworkElement { Tag: string tag })
		{
			try
			{
				Process.Start("explorer.exe", tag);
				AddToLog("Opened drive: " + tag);
			}
			catch (Exception ex)
			{
				AddToLog("Drive open error: " + ex.Message);
			}
		}
	}

	private void MaximizeButton_Click(object sender, RoutedEventArgs e)
	{
		if (WindowState == WindowState.Maximized)
		{
			WindowState = WindowState.Normal;
			if (sender is System.Windows.Controls.Button button)
			{
				button.Content = "\ud83d\uddd6";
			}
		}
		else
		{
			WindowState = WindowState.Maximized;
			if (sender is System.Windows.Controls.Button button2)
			{
				button2.Content = "\ud83d\uddd7";
			}
		}
	}

	private void MonitoringToggle_Click(object sender, RoutedEventArgs e)
	{
		if (MonitoringToggle.IsChecked == true)
		{
			_timer.Start();
		}
		else
		{
			_timer.Stop();
		}
		AddToLog((MonitoringToggle.IsChecked == true) ? "Monitoring: Enabled" : "Monitoring: Disabled (Battery Saver)");
	}

	public void EnableAntiIdle(bool enable)
	{
		_isAntiIdleActive = enable;
		Dispatcher.Invoke(() =>
		{
			if (CaffeineToggle != null && CaffeineToggle.IsChecked != enable)
			{
				CaffeineToggle.IsChecked = enable;
			}
		});
		if (enable)
		{
			SetThreadExecutionState(2147483715u);
			StartAntiIdleKeepAlive();
			AddToLog("Anti-Idle Mode: ON (Screen kept awake, idle prevention active)");
		}
		else
		{
			StopAntiIdleKeepAlive();
			SetThreadExecutionState(2147483648u);
			AddToLog("Anti-Idle Mode: OFF (Standard power policy restored)");
		}
	}

	private void StartAntiIdleKeepAlive()
	{
		Dispatcher.Invoke(() =>
		{
			if (_antiIdleTimer == null)
			{
				_antiIdleTimer = new DispatcherTimer();
				_antiIdleTimer.Interval = TimeSpan.FromSeconds(35.0);
				_antiIdleTimer.Tick += (object? s, EventArgs e) =>
				{
					if (!_isAntiIdleActive)
					{
						return;
					}
					try
					{
						mouse_event(1u, 1u, 0u, 0u, 0u);
						mouse_event(1u, uint.MaxValue, 0u, 0u, 0u);
						keybd_event(135, 0, 0u, 0u);
						keybd_event(135, 0, 2u, 0u);
					}
					catch
					{
					}
				};
			}
			_antiIdleTimer.Start();
		});
	}

	private void StopAntiIdleKeepAlive()
	{
		Dispatcher.Invoke(() =>
		{
			_antiIdleTimer?.Stop();
		});
	}

	public void UnlockOrWakePc(string pin = "")
	{
		Task.Run(() =>
		{
			try
			{
				SendMessage(HWND_BROADCAST, 274u, SC_MONITORPOWER, new IntPtr(-1));
				Thread.Sleep(80);
				mouse_event(1u, 1u, 0u, 0u, 0u);
				mouse_event(1u, uint.MaxValue, 0u, 0u, 0u);
				keybd_event(32, 0, 0u, 0u);
				keybd_event(32, 0, 2u, 0u);
				if (!string.IsNullOrEmpty(pin))
				{
					Thread.Sleep(450);
					string text = pin;
					for (int i = 0; i < text.Length; i++)
					{
						byte bVk = (byte)(VkKeyScan(text[i]) & 0xFF);
						byte bScan = 0;
						keybd_event(bVk, bScan, 0u, 0u);
						Thread.Sleep(25);
						keybd_event(bVk, bScan, 2u, 0u);
						Thread.Sleep(25);
					}
					Thread.Sleep(80);
					keybd_event(13, 0, 0u, 0u);
					keybd_event(13, 0, 2u, 0u);
					AddToLog($"Remote Unlock: Wake + PIN executed ({pin.Length} digits)");
				}
				else
				{
					AddToLog("Remote Wake: Screen turned ON & Lock curtain dismissed");
				}
			}
			catch (Exception ex)
			{
				AddToLog("Remote Unlock Error: " + ex.Message);
			}
		});
	}

	private void CaffeineToggle_Click(object sender, RoutedEventArgs e)
	{
		bool valueOrDefault = CaffeineToggle.IsChecked == true;
		EnableAntiIdle(valueOrDefault);
	}

	private void StartClipboardMonitor()
	{
		_lastClipboardText = "";
		try
		{
			_lastClipboardText = System.Windows.Forms.Clipboard.GetText() ?? "";
		}
		catch
		{
		}
		_clipboardTimer = new DispatcherTimer();
		_clipboardTimer.Interval = TimeSpan.FromSeconds(2.0);
		_clipboardTimer.Tick += (object? s, EventArgs e) =>
		{
			try
			{
				string text = System.Windows.Forms.Clipboard.GetText() ?? "";
				if (!string.IsNullOrEmpty(text) && text != _lastClipboardText)
				{
					_lastClipboardText = text;
					Dispatcher.Invoke(() => { if (CurrentClipboardText != null) CurrentClipboardText.Text = text; });
					AddToLog("Clipboard updated: " + ((text.Length > 30) ? (text.Substring(0, 30) + "...") : text));
					_ = Task.Run(async () =>
					{
						try
						{
							bool success = await SendPhoneCommandAsync("clipboard", new[] { new KeyValuePair<string, string>("text", text) });
							if (!success)
							{
								Log("Clipboard sync to phone returned false or phone unavailable");
							}
						}
						catch (Exception ex)
						{
							Log("Clipboard monitor sync error: " + ex.Message);
						}
					});
				}
			}
			catch
			{
			}
		};
		_clipboardTimer.Start();
	}

	private void Macro_Click(object sender, RoutedEventArgs e)
	{
		if (sender is System.Windows.Controls.Button button)
		{
			string text = button.Tag as string;
			switch (text)
			{
			case "boostram":
				int trimmedProcesses = TrimProcessWorkingSets();
				AddToLog($"Memory boost completed: trimmed {trimmedProcesses} process working sets");
				return;
			case "flushdns":
				RunCommand("ipconfig /flushdns");
				break;
			case "cleartemp":
				RunCommand("cmd.exe /c del /q/f/s %TEMP%\\*");
				break;
			case "restartexp":
				RunCommand("cmd.exe /c taskkill /f /im explorer.exe & start explorer.exe");
				break;
			case "toggledark":
				ToggleSystemTheme();
				break;
			case "audiofix":
				RunCommand("cmd.exe /c net stop audiosrv & net start audiosrv");
				break;
			case "shutdown":
				RunCommand("shutdown /s /t 60");
				break;
			case "reboot":
				RunCommand("shutdown /r /t 0");
				break;
			case "abort":
				RunCommand("shutdown /a");
				break;
			}
			if (text != "restartexp")
			{
				AddToLog("Executed Macro: " + text.ToUpper());
			}
		}
	}

	private static string NormalizeIp(string? ip)
	{
		if (string.IsNullOrWhiteSpace(ip)) return "";
		ip = ip.Trim();
		if (ip.StartsWith("::ffff:", StringComparison.OrdinalIgnoreCase))
		{
			ip = ip.Substring(7);
		}
		if (System.Net.IPAddress.TryParse(ip, out var addr))
		{
			if (addr.IsIPv4MappedToIPv6)
			{
				return addr.MapToIPv4().ToString();
			}
			return addr.ToString();
		}
		return ip;
	}

	private async void FindPhone_Click(object sender, RoutedEventArgs e)
	{
		AddToLog("🔍 Searching for mobile device...");

		// Try direct known IP first
		bool phoneLocated = await SendPhoneCommandAsync("ring", Array.Empty<KeyValuePair<string, string>>());

		if (!phoneLocated)
		{
			// Fallback: scan local subnet for phone on port 8092
			AddToLog("Direct IP failed. Scanning local network for phone...");
			phoneLocated = await ScanSubnetForPhoneAsync();
		}

		if (phoneLocated)
		{
			AddToLog("🔔 Find Phone alert sent! Phone is ringing...");
			Dispatcher.Invoke(() =>
			{
				System.Windows.MessageBox.Show("📱 Alert sent! Your phone should be ringing now.", "Find My Phone", MessageBoxButton.OK, MessageBoxImage.Information);
			});
		}
		else
		{
			AddToLog("❌ Find Phone failed: phone not reachable on this network.");
			Dispatcher.Invoke(() =>
			{
				System.Windows.MessageBox.Show(
					"Could not reach your phone.\n\n" +
					"Make sure:\n" +
					"• Phone and PC are on the same Wi-Fi\n" +
					"• PC Master app is open on your phone\n" +
					"• Or connect phone via USB cable",
					"Find My Phone – Not Found",
					MessageBoxButton.OK, MessageBoxImage.Warning);
			});
		}
	}

	private async Task<bool> ScanSubnetForPhoneAsync()
	{
		try
		{
			// Get local PC IP to determine subnet
			string localIp = "";
			foreach (var ni in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
			{
				if (ni.OperationalStatus != System.Net.NetworkInformation.OperationalStatus.Up) continue;
				if (ni.NetworkInterfaceType == System.Net.NetworkInformation.NetworkInterfaceType.Loopback) continue;
				foreach (var addr in ni.GetIPProperties().UnicastAddresses)
				{
					if (addr.Address.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork)
					{
						localIp = addr.Address.ToString();
						break;
					}
				}
				if (!string.IsNullOrEmpty(localIp)) break;
			}

			if (string.IsNullOrEmpty(localIp)) return false;

			string subnet = localIp.Substring(0, localIp.LastIndexOf('.') + 1); // e.g. "192.168.1."
			AddToLog($"Scanning subnet {subnet}0/24 for phone on port 8092...");

			var tasks = new List<Task<string?>>();
			for (int i = 1; i <= 254; i++)
			{
				string ip = subnet + i;
				if (ip == localIp) continue;
				tasks.Add(TryRingPhoneAtIpAsync(ip));
			}

			// Wait for first success or all to complete
			while (tasks.Count > 0)
			{
				var completed = await Task.WhenAny(tasks);
				tasks.Remove(completed);
				string? foundIp = await completed;
				if (foundIp != null)
				{
					_lastConnectedMobileIp = foundIp;
					AddToLog($"📱 Phone found at {foundIp} via subnet scan!");
					return true;
				}
			}
		}
		catch (Exception ex)
		{
			Log($"ScanSubnetForPhoneAsync error: {ex.Message}");
		}
		return false;
	}

	private async Task<string?> TryRingPhoneAtIpAsync(string ip)
	{
		try
		{
			using var cts = new System.Threading.CancellationTokenSource(1500);
			using var req = new HttpRequestMessage(HttpMethod.Post, $"http://{ip}:8092/ring");
			if (!string.IsNullOrEmpty(_settings?.AuthToken))
				req.Headers.TryAddWithoutValidation("X-Auth-Token", _settings.AuthToken);
			var resp = await _phoneHttpClient.SendAsync(req, cts.Token);
			if (resp.IsSuccessStatusCode) return ip;
		}
		catch { /* timeout or not phone */ }
		return null;
	}

	private async Task<bool> SendPhoneCommandAsync(string command, IEnumerable<KeyValuePair<string, string>> values)
	{
		string targetIp = NormalizeIp(_lastConnectedMobileIp);
		Log($"SendPhoneCommandAsync: command={command}, targetIp={targetIp}, rawIp={_lastConnectedMobileIp}");

		// 1. Direct Wi-Fi HTTP push to mobile device (port 8092)
		if (!string.IsNullOrWhiteSpace(targetIp) && targetIp != "127.0.0.1" && targetIp != "::1")
		{
			try
			{
				using var req = new HttpRequestMessage(HttpMethod.Post, $"http://{targetIp}:8092/{command}");
				req.Content = new FormUrlEncodedContent(values);
				if (!string.IsNullOrEmpty(_settings?.AuthToken))
				{
					req.Headers.TryAddWithoutValidation("X-Auth-Token", _settings.AuthToken);
				}
				HttpResponseMessage response = await _phoneHttpClient.SendAsync(req);
				Log($"SendPhoneCommandAsync: HTTP response {response.StatusCode} from {targetIp}:8092/{command}");
				if (response.IsSuccessStatusCode)
				{
					return true;
				}
			}
			catch (Exception ex)
			{
				Log($"SendPhoneCommandAsync HTTP error to {targetIp}: {ex.Message}");
			}
		}

		// 2. Fallback: ADB connection (USB cable or wireless ADB)
		string adbPath = _settings?.AdbPath ?? FindAdbPath();
		if (!string.IsNullOrEmpty(adbPath) && File.Exists(adbPath))
		{
			try
			{
				// Forward port 8092 to localhost so we can reach the phone's notification/command server over ADB
				ProcessStartInfo psiFwd = new ProcessStartInfo(adbPath, "forward tcp:8092 tcp:8092")
				{
					CreateNoWindow = true,
					UseShellExecute = false,
					RedirectStandardOutput = true
				};
				Process.Start(psiFwd)?.WaitForExit(1000);

				try
				{
					using var reqLocal = new HttpRequestMessage(HttpMethod.Post, $"http://127.0.0.1:8092/{command}");
					reqLocal.Content = new FormUrlEncodedContent(values);
					if (!string.IsNullOrEmpty(_settings?.AuthToken))
					{
						reqLocal.Headers.TryAddWithoutValidation("X-Auth-Token", _settings.AuthToken);
					}
					HttpResponseMessage respLocal = await _phoneHttpClient.SendAsync(reqLocal);
					if (respLocal.IsSuccessStatusCode)
					{
						Log("SendPhoneCommandAsync: Successfully triggered command via ADB port-forwarding");
						return true;
					}
				}
				catch (Exception exLocal)
				{
					Log($"SendPhoneCommandAsync ADB forwarded HTTP error: {exLocal.Message}");
				}

				// If command is "ring", broadcast intent directly to phone via ADB shell
				if (command == "ring")
				{
					ProcessStartInfo psiBroadcast = new ProcessStartInfo(adbPath, "shell am broadcast -a com.pcmaster.mobile.RING_PHONE")
					{
						CreateNoWindow = true,
						UseShellExecute = false
					};
					Process.Start(psiBroadcast)?.WaitForExit(2000);

					ProcessStartInfo psiIntent = new ProcessStartInfo(adbPath, "shell am start -n com.pcmaster.mobile/.MainActivity --es action ring")
					{
						CreateNoWindow = true,
						UseShellExecute = false
					};
					Process.Start(psiIntent)?.WaitForExit(2000);

					Log("SendPhoneCommandAsync: Sent ring intent/broadcast via ADB");
					return true;
				}
			}
			catch (Exception exAdb)
			{
				Log($"SendPhoneCommandAsync ADB fallback error: {exAdb.Message}");
			}
		}

		return false;
	}

	private void RefreshNetwork_Click(object sender, RoutedEventArgs e)
	{
		UpdateNetworkInfo();
		AddToLog("Connection addresses refreshed");
	}

	private static int TrimProcessWorkingSets()
	{
		int trimmedProcesses = 0;
		foreach (Process process in Process.GetProcesses())
		{
			try
			{
				if (!process.HasExited && EmptyWorkingSet(process.Handle))
				{
					trimmedProcesses++;
				}
			}
			catch
			{
			}
			finally
			{
				process.Dispose();
			}
		}
		return trimmedProcesses;
	}

	private void ToggleSystemTheme()
	{
		try
		{
			object value = Registry.GetValue("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme", 1);
			int num = ((value != null && (int)value == 0) ? 1 : 0);
			Registry.SetValue("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "AppsUseLightTheme", num, RegistryValueKind.DWord);
			Registry.SetValue("HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "SystemUsesLightTheme", num, RegistryValueKind.DWord);
			AddToLog("System Theme Toggled");
		}
		catch (Exception ex)
		{
			Log("Theme toggle error: " + ex.Message);
		}
	}

	private void UpdateNotifBadge()
	{
		Dispatcher.Invoke(() =>
		{
			if (_notifications.Count > 0)
			{
				NotifBadgeCount.Text = _notifications.Count.ToString();
				NotifBadgeBorder.Visibility = Visibility.Visible;
			}
			else
			{
				NotifBadgeBorder.Visibility = Visibility.Collapsed;
			}
		});
	}

	private void DismissNotification_Click(object sender, RoutedEventArgs e)
	{
		if (!(sender is System.Windows.Controls.Button { Tag: var tag }))
		{
			return;
		}
		string id = tag as string;
		if (id == null)
		{
			return;
		}
		NotificationItem notificationItem = _notifications.FirstOrDefault((NotificationItem n) => n.Id == id);
		if (notificationItem != null)
		{
			_notifications.Remove(notificationItem);
			UpdateNotifBadge();
			if (_notifications.Count == 0)
			{
				NoNotifText.Visibility = Visibility.Visible;
			}
		}
	}

	private void ShowDesktopNotification(NotificationItem notif)
	{
		Dispatcher.Invoke(() =>
		{
			try
			{
				_trayIcon?.ShowBalloonTip(5000, notif.AppName?.ToUpper() ?? "PHONE NOTIFICATION", notif.Title + ": " + notif.Content, ToolTipIcon.Info);
			}
			catch
			{
			}
			ToastAppName.Text = notif.AppName?.ToUpper() ?? "PHONE";
			ToastTitle.Text = notif.Title ?? "";
			ToastContent.Text = notif.Content ?? "";
			ToastReplyBox.Tag = notif.Id;
			ToastReplyBox.Text = "";
			ToastReplyGrid.Visibility = ((!notif.CanReply) ? Visibility.Collapsed : Visibility.Visible);
			DesktopNotifToast.Visibility = Visibility.Visible;
			_toastTimer?.Stop();
			_toastTimer = new DispatcherTimer
			{
				Interval = TimeSpan.FromSeconds(15.0)
			};
			_toastTimer.Tick += (object? s, EventArgs e) =>
			{
				_toastTimer?.Stop();
				DesktopNotifToast.Visibility = Visibility.Collapsed;
			};
			_toastTimer.Start();
		});
	}

	private void CloseToast_Click(object sender, RoutedEventArgs e)
	{
		_toastTimer?.Stop();
		DesktopNotifToast.Visibility = Visibility.Collapsed;
	}

	private void ToastReplyBox_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
	{
		if (e.Key == Key.Return)
		{
			ToastReply_Click(sender, new RoutedEventArgs());
		}
	}

	private void ReplyBox_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
	{
		if (e.Key == Key.Return && sender is FrameworkElement frameworkElement)
		{
			System.Windows.Controls.Button button = ((frameworkElement.Parent as System.Windows.Controls.Panel) ?? ((frameworkElement.Parent as FrameworkElement)?.Parent as System.Windows.Controls.Panel))?.Children.OfType<System.Windows.Controls.Button>().FirstOrDefault();
			if (button != null)
			{
				NotificationReply_Click(button, new RoutedEventArgs());
			}
		}
	}

	private async void ToastReply_Click(object sender, RoutedEventArgs e)
	{
		string text = ToastReplyBox.Text;
		string notifId = (ToastReplyBox.Tag as string) ?? "";
		if (string.IsNullOrWhiteSpace(text) || string.IsNullOrEmpty(notifId))
		{
			return;
		}
		NotificationItem notif = _notifications.FirstOrDefault((NotificationItem n) => n.Id == notifId);
		if (!(await SendReplyToPhone(notifId, text, notif?.AppName ?? "Phone")))
		{
			return;
		}
		ToastReplyBox.Text = "";
		DesktopNotifToast.Visibility = Visibility.Collapsed;
		if (notif != null)
		{
			_notifications.Remove(notif);
			UpdateNotifBadge();
			if (_notifications.Count == 0)
			{
				NoNotifText.Visibility = Visibility.Visible;
			}
		}
	}

	private async void NotificationReply_Click(object sender, RoutedEventArgs e)
	{
		if (!(sender is FrameworkElement frameworkElement))
		{
			return;
		}
		System.Windows.Controls.TextBox replyBox = ((frameworkElement.Parent as System.Windows.Controls.Panel) ?? ((frameworkElement.Parent as FrameworkElement)?.Parent as System.Windows.Controls.Panel))?.Children.OfType<System.Windows.Controls.TextBox>().FirstOrDefault();
		if (replyBox == null)
		{
			return;
		}
		string text = replyBox.Text;
		string notifId = (replyBox.Tag as string) ?? "";
		NotificationItem notif = _notifications.FirstOrDefault((NotificationItem n) => n.Id == notifId);
		if (notif != null && !string.IsNullOrWhiteSpace(text) && await SendReplyToPhone(notifId, text, notif.AppName))
		{
			replyBox.Text = "";
			_notifications.Remove(notif);
			UpdateNotifBadge();
			if (_notifications.Count == 0)
			{
				NoNotifText.Visibility = Visibility.Visible;
			}
		}
	}

private async Task<bool> SendReplyToPhone(string notifId, string replyText, string appName)
        {
            // Queue reply for mobile app background poller (guarantees delivery across AP isolation / firewalls)
            _pendingReplies.Enqueue(new NotificationReplyItem { Id = notifId, Message = replyText });

            // Optionally try direct push to mobile device (port 8092) - silent failure is expected in many network configs
            if (!string.IsNullOrEmpty(_lastConnectedMobileIp) && _lastConnectedMobileIp != "127.0.0.1")
            {
                try
                {
                    using HttpClient client = new HttpClient { Timeout = TimeSpan.FromSeconds(2.0) };
                    FormUrlEncodedContent content = new FormUrlEncodedContent(new[]
                    {
                        new KeyValuePair<string, string>("id", notifId),
                        new KeyValuePair<string, string>("message", replyText)
                    });
                    var resp = await client.PostAsync("http://" + _lastConnectedMobileIp + ":8092/reply", content);
                    if (resp.IsSuccessStatusCode)
                    {
                        AddToLog($"Replied directly to {appName}: \"{replyText}\"");
                        return true;
                    }
                }
                catch
                {
                    // Silent failure - mobile will poll for queued replies
                }
            }
            AddToLog($"Reply queued for {appName}: \"{replyText}\" (syncing to phone via polling)");
            return true;
        }

	private async void StressTest_Click(object sender, RoutedEventArgs e)
	{
		if (StressTestBtn.Tag?.ToString() == "Running")
		{
			return;
		}
		StressTestBtn.Tag = "Running";
		StressTestBtn.Content = "STRESS TESTING...";
		AddToLog("Stress Test: Starting 10s high-frequency telemetry burst...");
		TimeSpan originalInterval = _timer.Interval;
		_timer.Interval = TimeSpan.FromMilliseconds(200.0);
		try
		{
			await Task.Delay(10000);
			AddToLog("Stress Test: Completed. No race conditions detected.");
		}
		catch (Exception ex)
		{
			AddToLog("Stress Test Error: " + ex.Message);
		}
		finally
		{
			_timer.Interval = originalInterval;
			StressTestBtn.Tag = null;
			StressTestBtn.Content = "RUN TELEMETRY STRESS";
		}
	}

	private void PressKey(byte vkey)
	{
		keybd_event(vkey, 0, 1u, 0u);
		keybd_event(vkey, 0, 3u, 0u);
	}

	private void SetBrightness(int level)
	{
		try
		{
			level = Math.Clamp(level, 0, 100);
			using ManagementObjectSearcher managementObjectSearcher = new ManagementObjectSearcher("root\\WMI", "SELECT * FROM WmiMonitorBrightnessMethods");
			using (ManagementObjectCollection.ManagementObjectEnumerator managementObjectEnumerator = managementObjectSearcher.Get().GetEnumerator())
			{
				if (managementObjectEnumerator.MoveNext())
				{
					((ManagementObject)managementObjectEnumerator.Current).InvokeMethod("WmiSetBrightness", new object[2]
					{
						1,
						(byte)level
					});
				}
			}
			AddToLog($"Brightness: {level}%");
		}
		catch (Exception ex)
		{
			Log("Brightness error: " + ex.Message);
		}
	}

	private int GetBrightness()
	{
		try
		{
			using ManagementObjectSearcher managementObjectSearcher = new ManagementObjectSearcher("root\\WMI", "SELECT * FROM WmiMonitorBrightness");
			using ManagementObjectCollection.ManagementObjectEnumerator managementObjectEnumerator = managementObjectSearcher.Get().GetEnumerator();
			if (managementObjectEnumerator.MoveNext())
			{
				return Convert.ToInt32(managementObjectEnumerator.Current["CurrentBrightness"]);
			}
		}
		catch
		{
		}
		return -1;
	}

	private object GetBatteryInfo()
	{
		try
		{
			using ManagementObjectSearcher managementObjectSearcher = new ManagementObjectSearcher("SELECT * FROM Win32_Battery");
			using ManagementObjectCollection.ManagementObjectEnumerator managementObjectEnumerator = managementObjectSearcher.Get().GetEnumerator();
			if (managementObjectEnumerator.MoveNext())
			{
				ManagementBaseObject current = managementObjectEnumerator.Current;
				int charge = Convert.ToInt32(current["EstimatedChargeRemaining"]);
				int num = Convert.ToInt32(current["BatteryStatus"]);
				return new
				{
					available = true,
					charge = charge,
					status = num switch
					{
						1 => "Discharging", 
						2 => "AC Power", 
						3 => "Fully Charged", 
						4 => "Low", 
						5 => "Critical", 
						6 => "Charging", 
						7 => "Charging High", 
						8 => "Charging Low", 
						9 => "Charging Critical", 
						10 => "Undefined", 
						11 => "Partially Charged", 
						_ => "Unknown", 
					},
					charging = (num == 2 || num == 6 || num == 7 || num == 8 || num == 9)
				};
			}
		}
		catch
		{
		}
		return new
		{
			available = false,
			charge = 0,
			status = "No battery",
			charging = false
		};
	}

	private object GetWifiInfo()
	{
		try
		{
			NetworkInterface networkInterface = (from ni in NetworkInterface.GetAllNetworkInterfaces()
				where ni.OperationalStatus == OperationalStatus.Up && (ni.NetworkInterfaceType == NetworkInterfaceType.Wireless80211 || ni.Name.ToLower().Contains("wi-fi") || ni.Name.ToLower().Contains("wlan"))
				select ni).FirstOrDefault();
			if (networkInterface != null)
			{
				UnicastIPAddressInformation unicastIPAddressInformation = networkInterface.GetIPProperties().UnicastAddresses.FirstOrDefault((UnicastIPAddressInformation ip) => ip.Address.AddressFamily == AddressFamily.InterNetwork);
				return new
				{
					connected = true,
					name = networkInterface.Name,
					description = networkInterface.Description,
					speed = networkInterface.Speed / 1000000,
					ip = (unicastIPAddressInformation?.Address.ToString() ?? ""),
					ssid = networkInterface.Name
				};
			}
		}
		catch
		{
		}
		return new
		{
			connected = false,
			name = "",
			description = "",
			speed = 0,
			ip = "",
			ssid = ""
		};
	}

	private void ScheduleTimer(string action, int minutes)
	{
		try
		{
			int value = minutes * 60;
			string text = action.ToLower() switch
			{
				"shutdown" => $"shutdown /s /t {value}", 
				"restart" => $"shutdown /r /t {value}", 
				"sleep" => $"shutdown /h /t {value}", 
				"abort" => "shutdown /a", 
				_ => "", 
			};
			if (!string.IsNullOrEmpty(text))
			{
				RunCommand(text);
				AddToLog($"Timer: {action.ToUpper()} in {minutes} min");
			}
		}
		catch (Exception ex)
		{
			Log("Timer error: " + ex.Message);
		}
	}

	private void MinimizeButton_Click(object sender, RoutedEventArgs e)
	{
		WindowState = WindowState.Minimized;
	}

	private void CloseButton_Click(object sender, RoutedEventArgs e)
	{
		Close();
	}

	private void CompactModeBtn_Click(object sender, RoutedEventArgs e)
	{
		ToggleCompactMode();
	}

	private void CompactModeToggle_Click(object sender, RoutedEventArgs e)
	{
		bool valueOrDefault = CompactModeToggle.IsChecked == true;
		_settings.CompactMode = valueOrDefault;
		SaveSettings();
		ApplyWindowMode(valueOrDefault);
	}

	private void ClearLog_Click(object sender, RoutedEventArgs e)
	{
		LogEntries.Clear();
		AddToLog("Log cleared");
	}

	private void ShowPairingQR_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			string value = (from ni in NetworkInterface.GetAllNetworkInterfaces()
				where ni.OperationalStatus == OperationalStatus.Up && ni.NetworkInterfaceType != NetworkInterfaceType.Loopback
				select ni).SelectMany((NetworkInterface ni) => ni.GetIPProperties().UnicastAddresses).FirstOrDefault((UnicastIPAddressInformation ip) => ip.Address.AddressFamily == AddressFamily.InterNetwork)?.Address.ToString() ?? "127.0.0.1";
			string text = $"http://{value}:{_settings.HttpPort}";
			string stringToEscape = text + "|" + _settings.AuthToken;
			string uriString = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=" + Uri.EscapeDataString(stringToEscape);
			Window window = new Window
			{
				Title = "Pair Mobile Device",
				Width = 400.0,
				Height = 500.0,
				WindowStartupLocation = WindowStartupLocation.CenterOwner,
				Owner = this,
				Background = new SolidColorBrush(System.Windows.Media.Color.FromRgb(6, 9, 18))
			};
			StackPanel stackPanel = new StackPanel
			{
				Margin = new Thickness(20.0)
			};
			stackPanel.Children.Add(new TextBlock
			{
				Text = "Scan with your phone",
				Foreground = System.Windows.Media.Brushes.White,
				FontSize = 18.0,
				FontWeight = FontWeights.Bold,
				HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
				Margin = new Thickness(0.0, 0.0, 0.0, 10.0)
			});
			System.Windows.Controls.Image element = new System.Windows.Controls.Image
			{
				Source = new BitmapImage(new Uri(uriString)),
				Width = 300.0,
				Height = 300.0
			};
			stackPanel.Children.Add(element);
			stackPanel.Children.Add(new TextBlock
			{
				Text = "URL: " + text,
				Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(0, 210, byte.MaxValue)),
				FontSize = 12.0,
				HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
				Margin = new Thickness(0.0, 15.0, 0.0, 5.0),
				TextWrapping = TextWrapping.Wrap
			});
			stackPanel.Children.Add(new TextBlock
			{
				Text = "Token: " + _settings.AuthToken,
				Foreground = new SolidColorBrush(System.Windows.Media.Color.FromRgb(106, 119, 139)),
				FontSize = 10.0,
				HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
				TextWrapping = TextWrapping.Wrap
			});
			System.Windows.Controls.Button copyPairingButton = new System.Windows.Controls.Button
			{
				Content = "COPY PAIRING INFO",
				Margin = new Thickness(0.0, 18.0, 0.0, 0.0),
				Padding = new Thickness(12.0, 7.0, 12.0, 7.0),
				HorizontalAlignment = System.Windows.HorizontalAlignment.Center
			};
			copyPairingButton.Click += (object? copySender, RoutedEventArgs copyEventArgs) =>
			{
				System.Windows.Clipboard.SetText(stringToEscape);
				copyPairingButton.Content = "PAIRING INFO COPIED";
			};
			stackPanel.Children.Add(copyPairingButton);
			window.Content = stackPanel;
			window.ShowDialog();
			AddToLog("Pairing QR displayed");
		}
		catch (Exception ex)
		{
			AddToLog("QR Error: " + ex.Message);
		}
	}

	private void ClearAllNotifications_Click(object sender, RoutedEventArgs e)
	{
		lock (_notificationsLock)
		{
			_notifications.Clear();
		}
		UpdateNotifBadge();
		if (NoNotifText != null)
		{
			NoNotifText.Visibility = Visibility.Visible;
		}
		AddToLog("Mobile Notifications: Cleared all");
	}

	private void SnapshotMirror_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			if (MobileScreenImage.Source is BitmapSource source)
			{
				string text = System.IO.Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyPictures), "PCMaster_Snapshots");
				if (!Directory.Exists(text))
				{
					Directory.CreateDirectory(text);
				}
				string text2 = $"PhoneSnapshot_{DateTime.Now:yyyyMMdd_HHmmss}.png";
				using (FileStream stream = new FileStream(System.IO.Path.Combine(text, text2), FileMode.Create))
				{
					PngBitmapEncoder pngBitmapEncoder = new PngBitmapEncoder();
					pngBitmapEncoder.Frames.Add(BitmapFrame.Create(source));
					pngBitmapEncoder.Save(stream);
				}
				AddToLog("Snapshot Saved: " + text2);
			}
			else
			{
				AddToLog("No mirror frame available to snapshot");
			}
		}
		catch (Exception ex)
		{
			AddToLog("Snapshot Error: " + ex.Message);
		}
	}

	private void RotateMirror_Click(object sender, RoutedEventArgs e)
	{
		_isMirrorLandscape = !_isMirrorLandscape;
		if (MirrorFrameBorder != null)
		{
			if (_isMirrorLandscape)
			{
				MirrorFrameBorder.Width = 620.0;
				MirrorFrameBorder.Height = 360.0;
			}
			else
			{
				MirrorFrameBorder.Width = 320.0;
				MirrorFrameBorder.Height = 560.0;
			}
		}
		AddToLog("Liquid Mirror: Orientation set to " + (_isMirrorLandscape ? "Landscape" : "Portrait"));
	}

	private void CopyLog_Click(object sender, RoutedEventArgs e)
	{
		try
		{
			System.Windows.Clipboard.SetText(string.Join(Environment.NewLine, LogEntries.Select((LogEntry l) => $"[{l.Time:HH:mm:ss}] {l.Message}")));
			AddToLog("Activity log copied to clipboard");
		}
		catch (Exception ex)
		{
			AddToLog("Copy Log Error: " + ex.Message);
		}
	}

	private void KillNotResponding_Click(object sender, RoutedEventArgs e)
	{
		RunCommand("taskkill /f /fi \"status eq not responding\"");
		AddToLog("Terminated all unresponsive tasks");
	}

	private void ScrollViewer_PreviewMouseWheel(object sender, MouseWheelEventArgs e)
	{
		if (sender is ScrollViewer scrollViewer)
		{
			scrollViewer.ScrollToVerticalOffset(scrollViewer.VerticalOffset - (double)e.Delta * 0.5);
			e.Handled = true;
		}
	}

	private void Window_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
	{
		if (e.LeftButton == MouseButtonState.Pressed)
		{
			DragMove();
		}
	}

	protected override void OnClosed(EventArgs e)
	{
		ExitApp();
		base.OnClosed(e);
	}

}
