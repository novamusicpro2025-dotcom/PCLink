using System;
using System.IO;
using System.IO.Compression;
using System.Diagnostics;
using System.Reflection;
using System.Windows.Forms;

[assembly: AssemblyTitle("PC Master Control Center Portable")]
[assembly: AssemblyProduct("PC Master Suite")]
[assembly: AssemblyCompany("Beyond Edge")]
[assembly: AssemblyVersion("2.5.3.0")]
[assembly: AssemblyFileVersion("2.5.3.0")]

namespace PcMasterLauncher
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            string targetDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PCMasterControl");
            string logPath = Path.Combine(targetDir, "Bootstrapper.log");
            try
            {
                if (!Directory.Exists(targetDir))
                {
                    Directory.CreateDirectory(targetDir);
                }
                File.AppendAllText(logPath, string.Format("[{0}] Bootstrapper launched. Args: {1}\n", DateTime.Now, string.Join(" ", args)));

                string exePath = Path.Combine(targetDir, "PcClient.exe");

                Assembly asm = Assembly.GetExecutingAssembly();
                string[] resNames = asm.GetManifestResourceNames();
                string zipResource = Array.Find(resNames, delegate(string r) { return r.EndsWith(".zip", StringComparison.OrdinalIgnoreCase); }) ?? "PC_Master_Portable.zip";
                File.AppendAllText(logPath, string.Format("[{0}] Selected zip resource: {1}\n", DateTime.Now, zipResource));

                using (Stream stream = asm.GetManifestResourceStream(zipResource))
                {
                    if (stream != null)
                    {
                        using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
                        {
                            foreach (ZipArchiveEntry entry in archive.Entries)
                            {
                                string destPath = Path.Combine(targetDir, entry.FullName);
                                if (string.IsNullOrEmpty(entry.Name))
                                {
                                    if (!Directory.Exists(destPath)) Directory.CreateDirectory(destPath);
                                }
                                else
                                {
                                    string dir = Path.GetDirectoryName(destPath);
                                    if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);

                                    try
                                    {
                                        entry.ExtractToFile(destPath, true);
                                    }
                                    catch (Exception exExtract)
                                    {
                                        File.AppendAllText(logPath, string.Format("[{0}] Extract skip for {1}: {2}\n", DateTime.Now, entry.FullName, exExtract.Message));
                                    }
                                }
                            }
                        }
                        File.AppendAllText(logPath, string.Format("[{0}] Extraction completed.\n", DateTime.Now));
                    }
                    else
                    {
                        File.AppendAllText(logPath, string.Format("[{0}] ERROR: Stream for {1} is null!\n", DateTime.Now, zipResource));
                    }
                }

                if (File.Exists(exePath))
                {
                    File.AppendAllText(logPath, string.Format("[{0}] Starting process: {1}\n", DateTime.Now, exePath));
                    ProcessStartInfo psi = new ProcessStartInfo(exePath);
                    psi.WorkingDirectory = targetDir;
                    psi.Arguments = string.Join(" ", args);
                    psi.UseShellExecute = true;
                    Process p = Process.Start(psi);
                    File.AppendAllText(logPath, string.Format("[{0}] Process started. PID: {1}\n", DateTime.Now, (p != null ? p.Id : -1)));
                }
                else
                {
                    File.AppendAllText(logPath, string.Format("[{0}] ERROR: exePath not found at {1}\n", DateTime.Now, exePath));
                    MessageBox.Show("Could not find PcClient.exe at " + exePath, "PC Master Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
            catch (Exception ex)
            {
                try { File.AppendAllText(logPath, string.Format("[{0}] FATAL: {1}\n", DateTime.Now, ex)); } catch { }
                MessageBox.Show("Error running PC Master: " + ex.Message, "PC Master Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
