# Print Wareify - Desktop Print Manager

Aplikasi Windows Desktop (Standalone Pure GUI) untuk mengelola printer barcode Zebra (USB Fisik dan Virtual TCP), polling print job, dan melakukan sinkronisasi dua arah dengan API Wareify / Enterprise Asset Management (EAM).

---

## 🌟 Fitur Utama

1. **Standalone Windows Desktop (.exe)**:
   - Berjalan sebagai aplikasi GUI native murni tanpa jendela command prompt/CMD hitam.
   - Dilengkapi icon aplikasi (*favicon*) pada jendela, taskbar, file `.exe`, dan system tray.

2. **Deteksi Dual Printer (USB & Virtual TCP)**:
   - **USB Fisik**: Mendeteksi printer Zebra fisik yang terhubung via koneksi USB langsung maupun Zebra Windows Driver (`ZSDK_API.jar`, `ZebraNativeUsbAdapter_64.dll`, `zDriverAdapter64.dll`).
   - **Virtual TCP**: Mendukung emulator printer Zebra via socket TCP (contoh: `127.0.0.1:9100`).

3. **Sinkronisasi Cerdas Master Printer (Smart Sync)**:
   - Polling status online/offline setiap detik.
   - Sinkronisasi instan ke server hanya ketika **status berubah** (`Online -> Offline` atau `Offline -> Online`).
   - Menggunakan alur pintar: Cek dulu ke server via `GET /printers?local_printer=...`, jika sudah ada lakukan `PUT /printers/{id}` (Update), jika belum ada lakukan `POST /printers` (Create).
   - Mendukung endpoint `POST /printers/heartbeat` untuk mencatat heartbeat live secara real-time.

4. **Sistem Autentikasi Header API**:
   - Kompatibel dengan autentikasi berbasis header `X-API-KEY`.

5. **Penyimpanan Konfigurasi Aman (`AppData\Local\Wareify`)**:
   - Konfigurasi disimpan di `%LOCALAPPDATA%\Wareify\` (`config.properties` & `printers.json`).
   - Aman saat aplikasi di-update atau laptop di-restart (tidak akan terpengaruh working directory Windows).

6. **Auto-Start on Boot & Silent Background Tray**:
   - Opsi auto-start otomatis saat Windows menyala.
   - Saat boot, aplikasi berjalan secara senyap (*silent background*) langsung ke **System Tray** tanpa memunculkan jendela utama yang mengganggu desktop.
   - Jendela utama dapat dimunculkan kapan saja dengan mengklik ikon di System Tray.

7. **Rotasi Log Harian Otomatis**:
   - File log tersimpan di `%LOCALAPPDATA%\Wareify\logs\print-wareify-YYYY-MM-DD.log`.
   - Otomatis membersihkan dan menghapus file log yang berumur lebih dari 30 hari (1 bulan).

8. **Installer Setup Mandiri**:
   - Disediakan paket installer wizard Windows (`Print-Wareify-Setup-v1.3.0.exe`) yang dibuat menggunakan Inno Setup.

---

## 🖥️ Antarmuka Pengguna (UI)

- **Tab Pengaturan API & Service**:
  - Konfigurasi API Base URL, Header `X-API-KEY`, dan endpoint path (`listen-print-job`, `update-print-job`, `printers`).
  - Opsi koneksi TCP (Host & Port).
  - Interval polling (ms), saklar aktifkan polling job, dan saklar auto-start on boot.
  - Log real-time visual dengan tombol *Buka Folder Log* dan *Bersihkan Layar*.
- **Tab Master Device Printer**:
  - Tabel perangkat: Checkbox `Aktifkan?`, `ID Server`, `Alamat Perangkat`, `Nama Printer` (dapat diedit langsung), dan `Status` (`ONLINE` / `OFFLINE`).
  - Tombol aksi: `Pindai Printer`, `Simpan Perubahan`, dan `Sinkronkan ke Server`.
- **Tab Diagnosa & Test**:
  - Pindai jaringan dan tombol uji coba cetak (*test sample label*).

---

## 🛠️ Prasyarat Build

- **Java Development Kit (JDK)**: JDK 17 (64-bit) dengan utilitas `jpackage`.
- **Maven**: Tersedia melalui `./mvnw.cmd`.
- **Inno Setup 6**: Untuk meng-compile paket installer setup `.exe` (opsional jika hanya butuh portable app-image).

---

## 🚀 Cara Menjalankan & Build

### 1. Menjalankan di Mode Pengembangan (Development)
```bash
./mvnw.cmd spring-boot:run
```

### 2. Meng-compile Standalone EXE & Installer Setup
Cukup jalankan script batch otomatis:
```cmd
build-exe.bat
```
Script tersebut akan melakukan:
1. Maven compile & package fat JAR (`target/printprasi-0.0.1-SNAPSHOT.jar`).
2. Menghasilkan binary desktop portable dengan `jpackage` di `dist\Print Wareify\`.
3. Menyalin seluruh native DLL Zebra 64-bit (`libs/*`) ke folder runtime aplikasi.
4. Meng-compile file installer setup mandiri dengan Inno Setup ke `dist-installer\Print-Wareify-Setup-v1.3.0.exe`.

---

## 📂 Struktur Direktori Proyek

```
printer-java-master/
├── .mvn/                                # Maven wrapper configuration
├── libs/                                # Zebra SDK and Native DLLs (x64 & x86)
│   ├── ZSDK_API.jar
│   ├── ZebraNativeUsbAdapter_64.dll
│   └── zDriverAdapter64.dll
├── src/                                 # Kode Sumber Aplikasi
│   └── main/java/com/example/printprasi/printprasi/
│       ├── configurations/              # Konfigurasi aplikasi & manajemen properties
│       ├── controllers/                 # REST Controller
│       ├── helpers/                     # AppPathHelper, WindowsStartupHelper, DailyLogFileManager, dll.
│       ├── models/                      # Model konfigurasi perangkat printer
│       ├── requests/                    # DTO Print Job Payload
│       ├── services/                    # PrintJobListenerService & ZebraPrinterService
│       └── ui/                          # GUI Swing Desktop (MainWindow & LogBridge)
├── build-exe.bat                        # Skrip build otomasi (Maven + Jpackage + Inno Setup)
├── installer.iss                        # Skrip installer Inno Setup 6
├── favicon.ico                          # Icon aplikasi multi-resolusi
├── pom.xml                              # Konfigurasi Maven dependencies
└── README.md                            # Dokumentasi teknis proyek
```

---

## 🔒 Konfigurasi & Keamanan
- Jangan menyimpan token API atau konfigurasi server sensitif di dalam source code repository Git.
- File `config.properties` dan `printers.json` diabaikan oleh `.gitignore` dan dikelola secara terisolasi di folder `%LOCALAPPDATA%\Wareify\`.

---

## 📄 Lisensi
Hak Cipta © 2026 Wareify / Enterprise Asset Management. Seluruh hak cipta dilindungi undang-undang.
