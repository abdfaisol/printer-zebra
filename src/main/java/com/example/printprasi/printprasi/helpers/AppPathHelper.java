package com.example.printprasi.printprasi.helpers;

import java.io.File;

public class AppPathHelper {
    private static File appDataDir = null;

    /**
     * Mengembalikan direktori penyimpanan data aplikasi di:
     * Windows: %LOCALAPPDATA%\Wareify (misal: C:\Users\<user>\AppData\Local\Wareify)
     * Non-Windows / Fallback: user.home/.wareify
     */
    public static synchronized File getAppDirectory() {
        if (appDataDir != null) {
            return appDataDir;
        }

        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            File baseDir;
            if (localAppData != null && !localAppData.trim().isEmpty()) {
                baseDir = new File(localAppData, "Wareify");
            } else {
                String userHome = System.getProperty("user.home", ".");
                baseDir = new File(userHome, "AppData/Local/Wareify");
                if (!baseDir.exists()) {
                    baseDir = new File(userHome, ".wareify");
                }
            }

            if (!baseDir.exists()) {
                baseDir.mkdirs();
            }

            appDataDir = baseDir;
            return appDataDir;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Fallback default
        appDataDir = new File(".");
        return appDataDir;
    }

    public static File getConfigFile(String fileName) {
        File file = new File(getAppDirectory(), fileName);
        
        // Migrasi otomatis jika file belum ada di LocalAppData tapi ada di folder aplikasi saat ini
        if (!file.exists()) {
            try {
                File currentDirFile = new File(".", fileName);
                if (currentDirFile.exists()) {
                    java.nio.file.Files.copy(currentDirFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    File distFile = new File("dist/Print Wareify/" + fileName);
                    if (distFile.exists()) {
                        java.nio.file.Files.copy(distFile.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception ignored) {}
        }
        
        return file;
    }
}
