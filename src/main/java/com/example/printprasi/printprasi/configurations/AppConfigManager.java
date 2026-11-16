package com.example.printprasi.printprasi.configurations;

import com.example.printprasi.printprasi.models.PrinterDeviceConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class AppConfigManager {
    private static final String CONFIG_FILE_NAME = "config.properties";
    private static final String PRINTERS_FILE_NAME = "printers.json";

    // API Configurations
    private String apiBaseUrl = "http://localhost:8888/api/v1/printers";
    private String apiToken = "";
    private String pathListenJob = "listen-print-job";
    private String pathUpdateJob = "update-print-job";
    private String pathPrinters = ""; // base path untuk CRUD master printer (GET /printers, POST /printers, PUT /printers/{id})

    // Polling Configurations
    private boolean pollingEnabled = true;
    private int pollingIntervalMs = 1000;
    private boolean autoSyncPrinters = true;
    private boolean runAtStartup = false;

    // TCP / Virtual ZPL Printer Options
    private boolean tcpEnabled = false;
    private String tcpHost = "127.0.0.1";
    private int tcpPort = 9100;

    // Managed Printers: address -> PrinterDeviceConfig
    private final Map<String, PrinterDeviceConfig> printerMap = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AppConfigManager() {
        loadConfig();
        loadPrinters();
    }

    public synchronized void loadConfig() {
        File configFile = com.example.printprasi.printprasi.helpers.AppPathHelper.getConfigFile(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            saveConfig();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(configFile)) {
            props.load(in);
            this.apiBaseUrl = props.getProperty("apiBaseUrl", this.apiBaseUrl);
            this.apiToken = props.getProperty("apiToken", this.apiToken);
            this.pathListenJob = props.getProperty("pathListenJob", this.pathListenJob);
            this.pathUpdateJob = props.getProperty("pathUpdateJob", this.pathUpdateJob);
            this.pathPrinters = props.getProperty("pathPrinters", props.getProperty("pathHeartbeat", this.pathPrinters));
            this.pollingEnabled = Boolean.parseBoolean(props.getProperty("pollingEnabled", String.valueOf(this.pollingEnabled)));
            this.pollingIntervalMs = Integer.parseInt(props.getProperty("pollingIntervalMs", String.valueOf(this.pollingIntervalMs)));
            this.autoSyncPrinters = Boolean.parseBoolean(props.getProperty("autoSyncPrinters", String.valueOf(this.autoSyncPrinters)));
            this.runAtStartup = Boolean.parseBoolean(props.getProperty("runAtStartup", String.valueOf(this.runAtStartup)));
            this.tcpEnabled = Boolean.parseBoolean(props.getProperty("tcpEnabled", String.valueOf(this.tcpEnabled)));
            this.tcpHost = props.getProperty("tcpHost", this.tcpHost);
            this.tcpPort = Integer.parseInt(props.getProperty("tcpPort", String.valueOf(this.tcpPort)));
        } catch (Exception e) {
            System.err.println("Gagal membaca config.properties: " + e.getMessage());
        }
    }

    public synchronized void saveConfig() {
        Properties props = new Properties();
        props.setProperty("apiBaseUrl", this.apiBaseUrl != null ? this.apiBaseUrl : "");
        props.setProperty("apiToken", this.apiToken != null ? this.apiToken : "");
        props.setProperty("pathListenJob", this.pathListenJob != null ? this.pathListenJob : "");
        props.setProperty("pathUpdateJob", this.pathUpdateJob != null ? this.pathUpdateJob : "");
        props.setProperty("pathPrinters", this.pathPrinters != null ? this.pathPrinters : "");
        props.setProperty("pollingEnabled", String.valueOf(this.pollingEnabled));
        props.setProperty("pollingIntervalMs", String.valueOf(this.pollingIntervalMs));
        props.setProperty("autoSyncPrinters", String.valueOf(this.autoSyncPrinters));
        props.setProperty("runAtStartup", String.valueOf(this.runAtStartup));
        props.setProperty("tcpEnabled", String.valueOf(this.tcpEnabled));
        props.setProperty("tcpHost", this.tcpHost != null ? this.tcpHost : "127.0.0.1");
        props.setProperty("tcpPort", String.valueOf(this.tcpPort));

        File configFile = com.example.printprasi.printprasi.helpers.AppPathHelper.getConfigFile(CONFIG_FILE_NAME);
        try (OutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "Print Wareify Configuration Settings");
        } catch (Exception e) {
            System.err.println("Gagal menyimpan config.properties: " + e.getMessage());
        }
    }

    public synchronized void loadPrinters() {
        File file = com.example.printprasi.printprasi.helpers.AppPathHelper.getConfigFile(PRINTERS_FILE_NAME);
        if (!file.exists()) {
            return;
        }
        try {
            List<PrinterDeviceConfig> list = objectMapper.readValue(file, new TypeReference<List<PrinterDeviceConfig>>() {});
            printerMap.clear();
            for (PrinterDeviceConfig cfg : list) {
                printerMap.put(cfg.getAddress(), cfg);
            }
        } catch (Exception e) {
            System.err.println("Gagal membaca printers.json: " + e.getMessage());
        }
    }

    public synchronized void savePrinters() {
        File file = com.example.printprasi.printprasi.helpers.AppPathHelper.getConfigFile(PRINTERS_FILE_NAME);
        try {
            List<PrinterDeviceConfig> list = new ArrayList<>(printerMap.values());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, list);
        } catch (Exception e) {
            System.err.println("Gagal menyimpan printers.json: " + e.getMessage());
        }
    }

    public synchronized String buildFullUrl(String path) {
        String base = apiBaseUrl != null ? apiBaseUrl.trim() : "";
        if (!base.endsWith("/")) {
            base += "/";
        }
        String p = path != null ? path.trim() : "";
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.isEmpty()) {
            return base.substring(0, base.length() - 1);
        }
        return base + p;
    }

    public String getFullListenJobUrl() {
        return buildFullUrl(pathListenJob);
    }

    public String getFullUpdateJobUrl() {
        return buildFullUrl(pathUpdateJob);
    }

    public String getFullPrintersUrl() {
        return buildFullUrl(pathPrinters);
    }

    // Getters and Setters
    public synchronized String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public synchronized void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public synchronized String getApiToken() {
        return apiToken;
    }

    public synchronized void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public synchronized String getPathListenJob() {
        return pathListenJob;
    }

    public synchronized void setPathListenJob(String pathListenJob) {
        this.pathListenJob = pathListenJob;
    }

    public synchronized String getPathUpdateJob() {
        return pathUpdateJob;
    }

    public synchronized void setPathUpdateJob(String pathUpdateJob) {
        this.pathUpdateJob = pathUpdateJob;
    }

    public synchronized String getPathPrinters() {
        return pathPrinters;
    }

    public synchronized void setPathPrinters(String pathPrinters) {
        this.pathPrinters = pathPrinters;
    }

    public synchronized boolean isPollingEnabled() {
        return pollingEnabled;
    }

    public synchronized void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }

    public synchronized int getPollingIntervalMs() {
        return pollingIntervalMs;
    }

    public synchronized void setPollingIntervalMs(int pollingIntervalMs) {
        this.pollingIntervalMs = pollingIntervalMs;
    }

    public synchronized boolean isAutoSyncPrinters() {
        return autoSyncPrinters;
    }

    public synchronized void setAutoSyncPrinters(boolean autoSyncPrinters) {
        this.autoSyncPrinters = autoSyncPrinters;
    }

    public synchronized boolean isRunAtStartup() {
        return runAtStartup;
    }

    public synchronized void setRunAtStartup(boolean runAtStartup) {
        this.runAtStartup = runAtStartup;
    }

    public synchronized boolean isTcpEnabled() {
        return tcpEnabled;
    }

    public synchronized void setTcpEnabled(boolean tcpEnabled) {
        this.tcpEnabled = tcpEnabled;
    }

    public synchronized String getTcpHost() {
        return tcpHost;
    }

    public synchronized void setTcpHost(String tcpHost) {
        this.tcpHost = tcpHost;
    }

    public synchronized int getTcpPort() {
        return tcpPort;
    }

    public synchronized void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public synchronized Map<String, PrinterDeviceConfig> getPrinterMap() {
        return printerMap;
    }

    public synchronized void updateOrAddPrinter(String address, String aliasName, Boolean selectedForSync, boolean online) {
        PrinterDeviceConfig cfg = printerMap.get(address);
        if (cfg == null) {
            boolean isSel = (selectedForSync != null) ? selectedForSync : true;
            cfg = new PrinterDeviceConfig(address, aliasName, isSel, online);
            printerMap.put(address, cfg);
        } else {
            // Hanya update aliasName jika printer belum punya alias tersimpan, atau jika aliasName diberikan eksplisit bukan null/kosong
            if (cfg.getAliasName() == null || cfg.getAliasName().trim().isEmpty()) {
                if (aliasName != null && !aliasName.trim().isEmpty()) {
                    cfg.setAliasName(aliasName);
                }
            }
            if (selectedForSync != null) {
                cfg.setSelectedForSync(selectedForSync);
            }
            cfg.setOnline(online);
        }
        savePrinters();
    }

    public synchronized void setPrinterAliasExplicitly(String address, String aliasName) {
        PrinterDeviceConfig cfg = printerMap.get(address);
        if (cfg != null) {
            cfg.setAliasName(aliasName);
            savePrinters();
        }
    }
}
