package com.example.printprasi.printprasi;

import com.example.printprasi.printprasi.configurations.AppConfigManager;
import com.example.printprasi.printprasi.services.ZebraPrinterService;
import com.example.printprasi.printprasi.ui.MainWindow;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;
import java.awt.GraphicsEnvironment;

@SpringBootApplication
@EnableScheduling
public class PrintprasiApplication {

  public static void main(String[] args) {
    com.example.printprasi.printprasi.helpers.NativeLibraryLoader.loadNativeLibrary();
    
    // Cek apakah dipanggil dengan argumen --minimized (misal saat boot auto-start)
    boolean startMinimized = false;
    if (args != null) {
      for (String arg : args) {
        if ("--minimized".equalsIgnoreCase(arg) || "-minimized".equalsIgnoreCase(arg) || "/minimized".equalsIgnoreCase(arg)) {
          startMinimized = true;
          break;
        }
      }
    }
    final boolean shouldStartMinimized = startMinimized;

    // Nonaktifkan headless mode agar Swing GUI dapat tampil
    ConfigurableApplicationContext context = new SpringApplicationBuilder(PrintprasiApplication.class)
            .headless(false)
            .run(args);

    if (!GraphicsEnvironment.isHeadless()) {
      SwingUtilities.invokeLater(() -> {
        AppConfigManager configManager = context.getBean(AppConfigManager.class);
        ZebraPrinterService zebraPrinterService = context.getBean(ZebraPrinterService.class);
        com.example.printprasi.printprasi.services.PrintJobListenerService printJobListenerService =
                context.getBean(com.example.printprasi.printprasi.services.PrintJobListenerService.class);
        MainWindow mainWindow = new MainWindow(configManager, zebraPrinterService, printJobListenerService);
        if (!shouldStartMinimized) {
          mainWindow.setVisible(true);
        } else {
          // Tetap sembunyikan jendela utama, hanya running di system tray
          mainWindow.setVisible(false);
        }
      });
    }
  }
}
