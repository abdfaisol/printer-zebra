package com.example.printprasi.printprasi.helpers;

import java.io.File;

public class NativeLibraryLoader {
  private static boolean loaded = false;

  public static synchronized void loadNativeLibrary() {
    if (loaded) return;

    // Coba load dari folder libs lokal
    String[] dllNames = {
      "ZebraNativeUsbAdapter_64.dll",
      "zDriverAdapter64.dll",
      "ZebraNativeUsbAdapter_32.dll",
      "zDriverAdapter.dll"
    };

    String[] searchPaths = {
      "libs",
      "app/libs",
      "../libs",
      "."
    };

    for (String dll : dllNames) {
      for (String path : searchPaths) {
        File file = new File(path, dll);
        if (file.exists()) {
          try {
            System.load(file.getAbsolutePath());
            System.out.println("Loaded native library: " + file.getAbsolutePath());
            break;
          } catch (Throwable t) {
            // Abaikan jika arsitektur 32 vs 64 mismatch
          }
        }
      }
    }
    loaded = true;
  }
}
