package com.example.printprasi.printprasi.helpers;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DailyLogFileManager {
    private static final String LOG_DIR = "logs";
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static long lastCleanupTime = 0;

    public static synchronized void appendLog(String message) {
        try {
            File dir = new File(com.example.printprasi.printprasi.helpers.AppPathHelper.getAppDirectory(), LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // Jalankan cleanup log lama sekali sehari (atau tiap interval tertentu)
            long now = System.currentTimeMillis();
            if (now - lastCleanupTime > 24 * 60 * 60 * 1000L) {
                lastCleanupTime = now;
                cleanOldLogs(30); // hapus log > 30 hari (1 bulan)
            }

            String todayDate = FILE_DATE_FORMAT.format(new Date(now));
            File logFile = new File(dir, "print-wareify-" + todayDate + ".log");

            try (PrintWriter out = new PrintWriter(new FileWriter(logFile, true))) {
                String timestamp = TIMESTAMP_FORMAT.format(new Date(now));
                out.println("[" + timestamp + "] " + message);
            }
        } catch (Exception e) {
            System.err.println("Gagal menulis file log: " + e.getMessage());
        }
    }

    public static void cleanOldLogs(int daysToKeep) {
        try {
            File dir = new File(com.example.printprasi.printprasi.helpers.AppPathHelper.getAppDirectory(), LOG_DIR);
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }

            long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60L * 60L * 1000L);
            File[] files = dir.listFiles((d, name) -> name.startsWith("print-wareify-") && name.endsWith(".log"));

            if (files != null) {
                for (File file : files) {
                    if (file.lastModified() < cutoffTime) {
                        boolean deleted = file.delete();
                        if (deleted) {
                            System.out.println("Log lama berhasil dihapus: " + file.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Gagal membersihkan log lama: " + e.getMessage());
        }
    }

    public static String getLogDirPath() {
        return new File(com.example.printprasi.printprasi.helpers.AppPathHelper.getAppDirectory(), LOG_DIR).getAbsolutePath();
    }
}
