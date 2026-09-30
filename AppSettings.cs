namespace PcClient;

public class AppSettings
{
	public string AdbPath { get; set; } = "";

	public string AuthToken { get; set; } = "";

	public bool RequireAuth { get; set; } = true;

	public int HttpPort { get; set; } = 8099;

	public int DiscoveryPort { get; set; } = 8091;

	public bool AutoStartHub { get; set; } = true;

	public bool MinimizeToTray { get; set; } = true;

	public bool RunOnStartup { get; set; }

	public int UpdateIntervalSeconds { get; set; } = 2;

	public bool EnableSsl { get; set; }

	public string SslCertPath { get; set; } = "";

	public string SslCertPassword { get; set; } = "";

	public bool CompactMode { get; set; }
}
