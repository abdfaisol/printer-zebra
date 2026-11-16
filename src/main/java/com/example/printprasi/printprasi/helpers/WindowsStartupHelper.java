package com.example.printprasi.printprasi.helpers;

import java.io.File;

public class WindowsStartupHelper {
    private static final String APP_NAME = "PrintWareify";
    private static final String REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    public static boolean setRunOnStartup(boolean enable) {
        try {
            if (enable) {
                // Cari lokasi executable saat ini
                String exePath = getExecutablePath();
                if (exePath == null) {
                    return false;
                }
                String cmd = String.format("reg add \"%s\" /v \"%s\" /t REG_SZ /d \"\\\"%s\\\" --minimized\" /f", REG_KEY, APP_NAME, exePath);
                Process process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
                return process.waitFor() == 0;
            } else {
                String cmd = String.format("reg delete \"%s\" /v \"%s\" /f", REG_KEY, APP_NAME);
                Process process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
                return process.waitFor() == 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isRunOnStartupEnabled() {
        try {
            String cmd = String.format("reg query \"%s\" /v \"%s\"", REG_KEY, APP_NAME);
            Process process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getExecutablePath() {
        try {
            // Coba periksa apakah running dari launcher jpackage
            File dir = new File(".").getCanonicalFile();
            File exeInDir = new File(dir, "Print Wareify.exe");
            if (exeInDir.exists()) {
                return exeInDir.getAbsolutePath();
            }

            // Alternatif jika berada di subfolder dist
            File exeInDist = new File(dir, "dist/Print Wareify/Print Wareify.exe");
            if (exeInDist.exists()) {
                return exeInDist.getCanonicalPath();
            }

            // Fallback via class location
            File codeSource = new File(WindowsStartupHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File parent = codeSource.getParentFile();
            if (parent != null && parent.getParentFile() != null) {
                File exeNear = new File(parent.getParentFile(), "Print Wareify.exe");
                if (exeNear.exists()) {
                    return exeNear.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
