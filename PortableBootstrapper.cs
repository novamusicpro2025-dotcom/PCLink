using System;
using System.IO;
using System.IO.Compression;
using System.Diagnostics;
using System.Reflection;
using System.Windows.Forms;

namespace PcMasterLauncher
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            try
            {
                string targetDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PCMasterControl");
                string exePath = Path.Combine(targetDir, "PcClient.exe");

                Assembly asm = Assembly.GetExecutingAssembly();
                using (Stream stream = asm.GetManifestResourceStream("PC_Master_v2.5_Portable.zip"))
                {
                    if (stream != null)
                    {
                        if (!Directory.Exists(targetDir))
                        {
                            Directory.CreateDirectory(targetDir);
                        }

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

                                    bool shouldExtract = !File.Exists(destPath) || (new FileInfo(destPath).Length != entry.Length);
                                    if (shouldExtract)
                                    {
                                        try
                                        {
                                            entry.ExtractToFile(destPath, true);
                                        }
                                        catch
                                        {
                                            // Ignored if file currently in use
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (File.Exists(exePath))
                {
                    ProcessStartInfo psi = new ProcessStartInfo(exePath);
                    psi.WorkingDirectory = targetDir;
                    psi.Arguments = string.Join(" ", args);
                    psi.UseShellExecute = true;
                    Process.Start(psi);
                }
                else
                {
                    MessageBox.Show("Could not find PcClient.exe at " + exePath, "PC Master Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Error running PC Master: " + ex.Message, "PC Master Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
