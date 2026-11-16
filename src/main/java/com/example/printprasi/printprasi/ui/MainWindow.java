package com.example.printprasi.printprasi.ui;

import com.example.printprasi.printprasi.configurations.AppConfigManager;
import com.example.printprasi.printprasi.models.DiscoveredPrinterInfo;
import com.example.printprasi.printprasi.models.PrinterDeviceConfig;
import com.example.printprasi.printprasi.services.PrintJobListenerService;
import com.example.printprasi.printprasi.services.ZebraPrinterService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class MainWindow extends JFrame {
    private final AppConfigManager configManager;
    private final ZebraPrinterService zebraPrinterService;
    private final PrintJobListenerService printJobListenerService;

    // Setting Fields
    private JTextField txtBaseUrl;
    private JTextField txtApiKey;
    private JTextField txtPathListen;
    private JTextField txtPathUpdate;
    private JTextField txtPathPrinters;
    private JSpinner spinnerInterval;
    private JCheckBox chkEnablePolling;
    private JCheckBox chkAutoSync;
    private JCheckBox chkRunAtStartup;

    // TCP Options
    private JCheckBox chkTcpEnabled;
    private JTextField txtTcpHost;
    private JSpinner spinnerTcpPort;

    // Logs & Status
    private JTextArea txtLogs;
    private JLabel lblPrinterStatus;
    private TrayIcon trayIcon;

    // Printer Table
    private JTable tblPrinters;
    private DefaultTableModel tableModel;

    public MainWindow(AppConfigManager configManager, ZebraPrinterService zebraPrinterService, PrintJobListenerService printJobListenerService) {
        this.configManager = configManager;
        this.zebraPrinterService = zebraPrinterService;
        this.printJobListenerService = printJobListenerService;

        initLookAndFeel();
        initUI();
        initSystemTray();
        initLogListener();
        refreshPrinterList();
        initAutoRefreshTimer();
    }

    private void initAutoRefreshTimer() {
        // Otomatis refresh status printer di tabel UI setiap 1 detik jika jendela sedang dibuka
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
            if (isVisible() && !tblPrinters.isEditing()) {
                refreshPrinterStatusOnly();
            }
        });
        timer.start();
    }

    private void refreshPrinterStatusOnly() {
        new Thread(() -> {
            Set<String> connected = new HashSet<>();

            // 1. Virtual / TCP
            if (configManager.isTcpEnabled()) {
                String tcpAddr = "tcp://" + configManager.getTcpHost() + ":" + configManager.getTcpPort();
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(configManager.getTcpHost(), configManager.getTcpPort()), 250);
                    connected.add(tcpAddr);
                } catch (Exception ignored) {}
            }

            // 2. USB Fisik
            List<DiscoveredPrinterInfo> physical = zebraPrinterService.findAllDiscoveredPrinters();
            for (DiscoveredPrinterInfo p : physical) {
                connected.add(p.getAddress());
            }

            Map<String, PrinterDeviceConfig> allPrinters = configManager.getPrinterMap();
            SwingUtilities.invokeLater(() -> {
                if (tblPrinters.isEditing()) return;
                int onlineCount = 0;
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String addr = (String) tableModel.getValueAt(i, 2);
                    boolean isOnline = connected.contains(addr);
                    if (isOnline) onlineCount++;
                    String currentStatus = (String) tableModel.getValueAt(i, 4);
                    String newStatus = isOnline ? "ONLINE" : "OFFLINE";
                    if (!newStatus.equals(currentStatus)) {
                        tableModel.setValueAt(newStatus, i, 4);
                    }
                    PrinterDeviceConfig cfg = allPrinters.get(addr);
                    if (cfg != null && cfg.isOnline() != isOnline) {
                        cfg.setOnline(isOnline);
                    }
                }
                if (onlineCount > 0) {
                    lblPrinterStatus.setText("Status: " + onlineCount + " printer terhubung");
                    lblPrinterStatus.setForeground(new Color(0, 150, 0));
                } else {
                    lblPrinterStatus.setText("Status: Tidak ada printer terhubung");
                    lblPrinterStatus.setForeground(new Color(220, 50, 50));
                }
            });
        }).start();
    }

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    private void initUI() {
        setTitle("Print Wareify - Zebra Print Manager");
        setSize(920, 700);
        setMinimumSize(new Dimension(820, 580));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        Image appIcon = loadAppIcon();
        if (appIcon != null) {
            setIconImage(appIcon);
        }

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // TAB 1: Setting API & Polling
        tabbedPane.addTab("Pengaturan API & Service", createConfigTab());

        // TAB 2: Master Device Printer
        tabbedPane.addTab("Master Device Printer", createPrinterManagementTab());

        // TAB 3: Diagnostic & Test
        tabbedPane.addTab("Diagnosa & Test", createDiagnosticTab());

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Footer Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        lblPrinterStatus = new JLabel("Status Printer: Memeriksa...");
        lblPrinterStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblPrinterStatus.setBorder(new EmptyBorder(6, 4, 4, 4));
        statusBar.add(lblPrinterStatus, BorderLayout.WEST);

        JLabel lblInfo = new JLabel("Print Wareify Desktop v1.3.0");
        lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        lblInfo.setForeground(Color.GRAY);
        statusBar.add(lblInfo, BorderLayout.EAST);

        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private JPanel createConfigTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        // Form Fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Konfigurasi Base URL & Endpoint (X-API-KEY)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 8, 3, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 1. API Base URL
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        formPanel.add(new JLabel("API Base URL:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txtBaseUrl = new JTextField(configManager.getApiBaseUrl());
        txtBaseUrl.setFont(new Font("Consolas", Font.PLAIN, 13));
        formPanel.add(txtBaseUrl, gbc);

        // 2. API Key Header
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        formPanel.add(new JLabel("X-API-KEY:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txtApiKey = new JTextField(configManager.getApiToken());
        txtApiKey.setFont(new Font("Consolas", Font.PLAIN, 13));
        formPanel.add(txtApiKey, gbc);

        // 3. Path Listen Job
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Path Listen Print Job:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txtPathListen = new JTextField(configManager.getPathListenJob());
        txtPathListen.setFont(new Font("Consolas", Font.PLAIN, 13));
        formPanel.add(txtPathListen, gbc);

        // 4. Path Update Job
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Path Update Status Job:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txtPathUpdate = new JTextField(configManager.getPathUpdateJob());
        txtPathUpdate.setFont(new Font("Consolas", Font.PLAIN, 13));
        formPanel.add(txtPathUpdate, gbc);

        // 5. Path Master Printers (CRUD GET / POST / PUT)
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Path Master Printer (CRUD):"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        txtPathPrinters = new JTextField(configManager.getPathPrinters());
        txtPathPrinters.setFont(new Font("Consolas", Font.PLAIN, 13));
        formPanel.add(txtPathPrinters, gbc);

        // 6. TCP / Virtual ZPL Options
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Virtual / TCP Printer:"), gbc);

        JPanel tcpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chkTcpEnabled = new JCheckBox("Gunakan TCP (Virtual Printer)", configManager.isTcpEnabled());
        chkTcpEnabled.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tcpPanel.add(chkTcpEnabled);

        tcpPanel.add(new JLabel("Host:"));
        txtTcpHost = new JTextField(configManager.getTcpHost(), 10);
        tcpPanel.add(txtTcpHost);

        tcpPanel.add(new JLabel("Port:"));
        spinnerTcpPort = new JSpinner(new SpinnerNumberModel(configManager.getTcpPort(), 1, 65535, 1));
        tcpPanel.add(spinnerTcpPort);

        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(tcpPanel, gbc);

        // 7. Polling Options & Auto Sync
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Pengaturan Service:"), gbc);

        JPanel pollPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pollPanel.add(new JLabel("Interval (ms):"));
        spinnerInterval = new JSpinner(new SpinnerNumberModel(configManager.getPollingIntervalMs(), 200, 60000, 100));
        pollPanel.add(spinnerInterval);

        chkEnablePolling = new JCheckBox("Aktifkan Polling Job", configManager.isPollingEnabled());
        chkEnablePolling.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pollPanel.add(chkEnablePolling);

        chkAutoSync = new JCheckBox("Auto-Sync Device (Smart Update)", configManager.isAutoSyncPrinters());
        chkAutoSync.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        pollPanel.add(chkAutoSync);

        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(pollPanel, gbc);

        // 8. Auto Start Windows Baris Terpisah
        gbc.gridx = 0; gbc.gridy = 7; gbc.weightx = 0.0;
        formPanel.add(new JLabel("Windows Startup:"), gbc);

        JPanel startupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        chkRunAtStartup = new JCheckBox("Jalankan Otomatis Saat Windows Start (Auto-Start on Boot)", configManager.isRunAtStartup());
        chkRunAtStartup.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chkRunAtStartup.setForeground(new Color(25, 118, 210));
        startupPanel.add(chkRunAtStartup);

        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(startupPanel, gbc);

        // Action Buttons Row
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        JButton btnReset = new JButton("Reset Default");
        JButton btnSave = new JButton("Simpan Pengaturan");
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSave.setPreferredSize(new Dimension(160, 32));

        btnReset.addActionListener(e -> {
            txtBaseUrl.setText("http://localhost:8888/api/v1/printers");
            txtApiKey.setText("");
            txtPathListen.setText("listen-print-job");
            txtPathUpdate.setText("update-print-job");
            txtPathPrinters.setText("");
            chkTcpEnabled.setSelected(false);
            txtTcpHost.setText("127.0.0.1");
            spinnerTcpPort.setValue(9100);
            spinnerInterval.setValue(1000);
            chkEnablePolling.setSelected(true);
            chkAutoSync.setSelected(true);
            chkRunAtStartup.setSelected(false);
        });

        btnSave.addActionListener(e -> saveSettings());

        btnPanel.add(btnReset);
        btnPanel.add(btnSave);

        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2;
        formPanel.add(btnPanel, gbc);

        panel.add(formPanel, BorderLayout.NORTH);

        // Logs Panel
        JPanel logContainer = new JPanel(new BorderLayout(4, 4));
        logContainer.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Aktivitas & Log Real-Time",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)));

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(30, 30, 30));
        txtLogs.setForeground(new Color(220, 220, 220));
        JScrollPane scrollLogs = new JScrollPane(txtLogs);

        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton btnOpenFolder = new JButton("📁 Buka Folder Log");
        JButton btnClearLog = new JButton("Bersihkan Layar");

        btnOpenFolder.addActionListener(e -> {
            try {
                java.io.File logFolder = new java.io.File(com.example.printprasi.printprasi.helpers.AppPathHelper.getAppDirectory(), "logs");
                if (!logFolder.exists()) {
                    logFolder.mkdirs();
                }
                Desktop.getDesktop().open(logFolder);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Folder log belum dibuat atau gagal dibuka: " + ex.getMessage());
            }
        });

        btnClearLog.addActionListener(e -> txtLogs.setText(""));

        logActions.add(btnOpenFolder);
        logActions.add(btnClearLog);

        logContainer.add(scrollLogs, BorderLayout.CENTER);
        logContainer.add(logActions, BorderLayout.SOUTH);

        panel.add(logContainer, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createPrinterManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Tool bar
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton btnScan = new JButton("Pindai Printer");
        btnScan.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JButton btnSaveAlias = new JButton("Simpan Perubahan");
        JButton btnSyncBackend = new JButton("Sinkronkan ke Server");

        toolBar.add(btnScan);
        toolBar.add(btnSaveAlias);
        toolBar.add(btnSyncBackend);

        panel.add(toolBar, BorderLayout.NORTH);

        // Table Model: Menampilkan Server ID agar transparan dan mudah dimonitor
        String[] columns = {"Aktifkan?", "ID Server", "Alamat Perangkat", "Nama Printer", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 3; // Checkbox dan Nama Alias bisa diedit
            }
        };

        tblPrinters = new JTable(tableModel);
        tblPrinters.setRowHeight(28);
        tblPrinters.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tblPrinters.getColumnModel().getColumn(0).setPreferredWidth(70);
        tblPrinters.getColumnModel().getColumn(1).setPreferredWidth(100);
        tblPrinters.getColumnModel().getColumn(2).setPreferredWidth(280);
        tblPrinters.getColumnModel().getColumn(3).setPreferredWidth(230);
        tblPrinters.getColumnModel().getColumn(4).setPreferredWidth(100);

        // Custom renderer untuk status Online / Offline
        tblPrinters.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String valStr = value != null ? value.toString() : "";
                if (valStr.contains("ONLINE")) {
                    l.setForeground(new Color(0, 150, 0));
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                    l.setText("ONLINE");
                } else {
                    l.setForeground(new Color(220, 50, 50));
                    l.setFont(l.getFont().deriveFont(Font.BOLD));
                    l.setText("OFFLINE");
                }
                return l;
            }
        });

        panel.add(new JScrollPane(tblPrinters), BorderLayout.CENTER);

        // Action Handlers
        btnScan.addActionListener(e -> refreshPrinterList());

        btnSaveAlias.addActionListener(e -> {
            if (tblPrinters.isEditing()) {
                tblPrinters.getCellEditor().stopCellEditing();
            }
            saveTableToConfig();
            JOptionPane.showMessageDialog(this, "Pengaturan & pilihan printer berhasil disimpan!", "Sukses", JOptionPane.INFORMATION_MESSAGE);
        });

        btnSyncBackend.addActionListener(e -> {
            if (tblPrinters.isEditing()) {
                tblPrinters.getCellEditor().stopCellEditing();
            }
            saveTableToConfig();

            new Thread(() -> {
                String res = printJobListenerService.syncPrintersToExternalApi(true);
                SwingUtilities.invokeLater(() -> {
                    refreshPrinterList();
                    JOptionPane.showMessageDialog(this, res, "Sinkronisasi Master Printer", JOptionPane.INFORMATION_MESSAGE);
                });
            }).start();
        });

        return panel;
    }

    private void saveTableToConfig() {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean isSync = (Boolean) tableModel.getValueAt(i, 0);
            String id = (String) tableModel.getValueAt(i, 1);
            String addr = (String) tableModel.getValueAt(i, 2);
            String alias = (String) tableModel.getValueAt(i, 3);
            String status = (String) tableModel.getValueAt(i, 4);
            boolean online = "🟢 ONLINE".equals(status);

            PrinterDeviceConfig cfg = configManager.getPrinterMap().get(addr);
            if (cfg == null) {
                cfg = new PrinterDeviceConfig(addr, alias, isSync, online);
                configManager.getPrinterMap().put(addr, cfg);
            } else {
                cfg.setAliasName(alias);
                cfg.setSelectedForSync(isSync);
                cfg.setOnline(online);
            }
            if (id != null && !id.trim().isEmpty() && !"-".equals(id)) {
                cfg.setId(id.trim());
            }
            configManager.setPrinterAliasExplicitly(addr, alias);
        }
        configManager.savePrinters();
    }

    private JPanel createDiagnosticTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton btnScanNetwork = new JButton("🌐 Scan Printer Jaringan");
        JButton btnPrintExample = new JButton("🖨️ Cetak Sample Label ke Printer Pertama");

        controlPanel.add(btnScanNetwork);
        controlPanel.add(btnPrintExample);
        panel.add(controlPanel, BorderLayout.NORTH);

        JTextArea txtDiagnostic = new JTextArea();
        txtDiagnostic.setEditable(false);
        txtDiagnostic.setFont(new Font("Consolas", Font.PLAIN, 12));
        panel.add(new JScrollPane(txtDiagnostic), BorderLayout.CENTER);

        btnScanNetwork.addActionListener(e -> {
            txtDiagnostic.setText("Memulai scanning jaringan printer...\n");
            new Thread(() -> {
                try {
                    Map<String, String> data = zebraPrinterService.findNetworkPrinter();
                    SwingUtilities.invokeLater(() -> txtDiagnostic.setText("Printer Jaringan:\n" + data.toString() + "\n"));
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> txtDiagnostic.setText("Error scan jaringan: " + ex.getMessage() + "\n"));
                }
            }).start();
        });

        btnPrintExample.addActionListener(e -> {
            new Thread(() -> {
                try {
                    Map<String, PrinterDeviceConfig> map = configManager.getPrinterMap();
                    String targetAddr = null;
                    for (PrinterDeviceConfig cfg : map.values()) {
                        if (cfg.isOnline()) {
                            targetAddr = cfg.getAddress();
                            break;
                        }
                    }
                    if (targetAddr == null) {
                        Map<String, String> data = zebraPrinterService.findLocalPrinter();
                        targetAddr = data.get("address");
                    }

                    if (targetAddr != null) {
                        LogBridge.log("Mencoba cetak sample label ke: " + targetAddr);
                        zebraPrinterService.printLabelExample(targetAddr);
                        LogBridge.log("Sample label berhasil dikirim.");
                    } else {
                        LogBridge.log("Tidak ada printer yang aktif untuk test print.");
                    }
                } catch (Exception ex) {
                    LogBridge.log("Gagal cetak sample label: " + ex.getMessage());
                }
            }).start();
        });

        return panel;
    }

    private void refreshPrinterList() {
        new Thread(() -> {
            Set<String> connected = new HashSet<>();

            // 1. Virtual / TCP Printer
            if (configManager.isTcpEnabled()) {
                String tcpAddr = "tcp://" + configManager.getTcpHost() + ":" + configManager.getTcpPort();
                boolean isTcpReachable = false;
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new java.net.InetSocketAddress(configManager.getTcpHost(), configManager.getTcpPort()), 300);
                    isTcpReachable = true;
                } catch (Exception ignored) {}

                if (isTcpReachable) {
                    connected.add(tcpAddr);
                    configManager.updateOrAddPrinter(tcpAddr, "Virtual ZPL Printer", null, true);
                } else {
                    PrinterDeviceConfig tcpCfg = configManager.getPrinterMap().get(tcpAddr);
                    if (tcpCfg != null) {
                        tcpCfg.setOnline(false);
                    }
                }
            }

            // 2. USB Fisik & Driver Printers
            List<DiscoveredPrinterInfo> physical = zebraPrinterService.findAllDiscoveredPrinters();
            for (DiscoveredPrinterInfo p : physical) {
                connected.add(p.getAddress());
                configManager.updateOrAddPrinter(p.getAddress(), p.getName(), null, true);
            }

            Map<String, PrinterDeviceConfig> allPrinters = configManager.getPrinterMap();

            SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                int onlineCount = 0;
                for (PrinterDeviceConfig cfg : allPrinters.values()) {
                    boolean isOnline = connected.contains(cfg.getAddress());
                    cfg.setOnline(isOnline);
                    if (isOnline) onlineCount++;

                    String alias = cfg.getAliasName() != null ? cfg.getAliasName() : "Printer Zebra " + (tableModel.getRowCount() + 1);
                    String idStr = (cfg.getId() != null && !cfg.getId().isEmpty()) ? cfg.getId() : "-";

                    tableModel.addRow(new Object[]{
                            cfg.isSelectedForSync(),
                            idStr,
                            cfg.getAddress(),
                            alias,
                            isOnline ? "ONLINE" : "OFFLINE"
                    });
                }

                if (onlineCount > 0) {
                    lblPrinterStatus.setText("Status: " + onlineCount + " printer terhubung");
                    lblPrinterStatus.setForeground(new Color(0, 150, 0));
                } else {
                    lblPrinterStatus.setText("Status: Tidak ada printer terhubung");
                    lblPrinterStatus.setForeground(new Color(220, 50, 50));
                }
            });
        }).start();
    }

    private void saveSettings() {
        configManager.setApiBaseUrl(txtBaseUrl.getText().trim());
        configManager.setApiToken(txtApiKey.getText().trim());
        configManager.setPathListenJob(txtPathListen.getText().trim());
        configManager.setPathUpdateJob(txtPathUpdate.getText().trim());
        configManager.setPathPrinters(txtPathPrinters.getText().trim());
        configManager.setTcpEnabled(chkTcpEnabled.isSelected());
        configManager.setTcpHost(txtTcpHost.getText().trim());
        configManager.setTcpPort((Integer) spinnerTcpPort.getValue());
        configManager.setPollingIntervalMs((Integer) spinnerInterval.getValue());
        configManager.setPollingEnabled(chkEnablePolling.isSelected());
        configManager.setAutoSyncPrinters(chkAutoSync.isSelected());
        configManager.setRunAtStartup(chkRunAtStartup.isSelected());

        com.example.printprasi.printprasi.helpers.WindowsStartupHelper.setRunOnStartup(chkRunAtStartup.isSelected());

        configManager.saveConfig();
        LogBridge.log("Konfigurasi API berhasil disimpan ke config.properties.");
        JOptionPane.showMessageDialog(this, "Pengaturan API berhasil disimpan!", "Informasi", JOptionPane.INFORMATION_MESSAGE);
    }

    private void initLogListener() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        LogBridge.addListener(msg -> SwingUtilities.invokeLater(() -> {
            String timestamp = sdf.format(new Date());
            txtLogs.append("[" + timestamp + "] " + msg + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        }));
    }

    private void initSystemTray() {
        if (!SystemTray.isSupported()) {
            return;
        }

        SystemTray tray = SystemTray.getSystemTray();
        Image image = loadAppIcon();

        PopupMenu popup = new PopupMenu();
        MenuItem itemOpen = new MenuItem("Buka Jendela");
        MenuItem itemExit = new MenuItem("Keluar");

        itemOpen.addActionListener(e -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
        });

        itemExit.addActionListener(e -> {
            System.exit(0);
        });

        popup.add(itemOpen);
        popup.addSeparator();
        popup.add(itemExit);

        trayIcon = new TrayIcon(image, "Print Wareify Service", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
        });

        try {
            tray.add(trayIcon);
        } catch (Exception e) {
            System.err.println("Gagal menginisialisasi System Tray: " + e.getMessage());
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (SystemTray.isSupported()) {
                    setVisible(false);
                    trayIcon.displayMessage("Print Wareify", "Aplikasi tetap berjalan di background.", TrayIcon.MessageType.INFO);
                } else {
                    System.exit(0);
                }
            }
        });
    }

    private Image loadAppIcon() {
        try {
            java.net.URL url = getClass().getResource("/icon.png");
            if (url != null) {
                return javax.imageio.ImageIO.read(url);
            }
            java.io.File file = new java.io.File("favicon.ico");
            if (file.exists()) {
                return javax.imageio.ImageIO.read(file);
            }
        } catch (Exception ignored) {}
        return createDefaultIcon();
    }

    private Image createDefaultIcon() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(25, 118, 210));
        g2.fillRoundRect(2, 2, 28, 28, 8, 8);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
        g2.drawString("W", 8, 23);
        g2.dispose();
        return img;
    }
}
