package com.example.printprasi.printprasi.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.printprasi.printprasi.configurations.AppConfigManager;
import com.example.printprasi.printprasi.models.DiscoveredPrinterInfo;
import com.example.printprasi.printprasi.models.PrinterDeviceConfig;
import com.example.printprasi.printprasi.ui.LogBridge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PrintJobListenerService {
  private final RestTemplate restTemplate = new RestTemplate();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ZebraPrinterService zebraPrinterService;
  private final AppConfigManager configManager;
  private int autoSyncCounter = 0;

  @Autowired
  public PrintJobListenerService(ZebraPrinterService zebraPrinterService, AppConfigManager configManager) {
    this.zebraPrinterService = zebraPrinterService;
    this.configManager = configManager;
  }

  public HttpHeaders createHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String apiKey = configManager.getApiToken();
    if (apiKey != null && !apiKey.trim().isEmpty()) {
      headers.set("X-API-Key", apiKey.trim());
    }
    return headers;
  }

  @Scheduled(fixedDelayString = "#{@appConfigManager.pollingIntervalMs}")
  public void checkForPrintJobs() {
    if (!configManager.isPollingEnabled()) {
      return;
    }

    String listenUrl = configManager.getFullListenJobUrl();
    if (listenUrl == null || listenUrl.trim().isEmpty()) {
      return;
    }

    try {
      // 1. Kumpulkan daftar printer aktif (TCP dan USB Fisik secara bersamaan)
      List<String> activeAddresses = new ArrayList<>();
      Map<String, String> addressToConnType = new HashMap<>();

      // A. Cek Virtual / TCP Printer jika aktif
      if (configManager.isTcpEnabled()) {
        String tcpAddr = "tcp://" + configManager.getTcpHost() + ":" + configManager.getTcpPort();
        boolean isTcpReachable = false;
        try (java.net.Socket socket = new java.net.Socket()) {
          socket.connect(new java.net.InetSocketAddress(configManager.getTcpHost(), configManager.getTcpPort()), 300);
          isTcpReachable = true;
        } catch (Exception ignored) {}

        if (isTcpReachable) {
          activeAddresses.add(tcpAddr);
          addressToConnType.put(tcpAddr, "network");
          configManager.updateOrAddPrinter(tcpAddr, "Virtual/TCP Printer", null, true);
        } else {
          PrinterDeviceConfig tcpCfg = configManager.getPrinterMap().get(tcpAddr);
          if (tcpCfg != null) {
            tcpCfg.setOnline(false);
          }
        }
      }

      // B. Scan semua printer USB fisik
      List<DiscoveredPrinterInfo> physicalPrinters = zebraPrinterService.findAllDiscoveredPrinters();
      Set<String> connectedUsb = new HashSet<>();
      for (DiscoveredPrinterInfo p : physicalPrinters) {
        connectedUsb.add(p.getAddress());
        activeAddresses.add(p.getAddress());
        addressToConnType.put(p.getAddress(), "usb");
        configManager.updateOrAddPrinter(p.getAddress(), p.getName(), null, true);
      }

      // Update status online/offline untuk semua printer yang terdaftar
      Map<String, PrinterDeviceConfig> registered = configManager.getPrinterMap();
      for (PrinterDeviceConfig cfg : registered.values()) {
        boolean isNowOnline;
        if (cfg.getAddress().startsWith("tcp://")) {
          isNowOnline = activeAddresses.contains(cfg.getAddress());
        } else {
          isNowOnline = connectedUsb.contains(cfg.getAddress());
        }

        // HANYA sinkron jika status printer BERUBAH (misal: Online -> Offline atau sebaliknya)
        if (cfg.isOnline() != isNowOnline) {
          cfg.setOnline(isNowOnline);
          if (configManager.isAutoSyncPrinters() && cfg.isSelectedForSync()) {
            String connType = cfg.getAddress().startsWith("tcp://") ? "network" : "usb";
            syncSinglePrinterSmart(cfg, connType, false);
            LogBridge.log("Status printer [" + cfg.getAliasName() + "] berubah menjadi: " + (isNowOnline ? "ONLINE" : "OFFLINE") + " -> tersinkron ke server.");
          }
        }
      }

      if (activeAddresses.isEmpty()) {
        return;
      }

      // 3. Polling print jobs untuk setiap printer aktif yang dicentang
      for (String addr : activeAddresses) {
        PrinterDeviceConfig cfg = registered.get(addr);
        if (cfg != null && !cfg.isSelectedForSync()) {
          continue;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("local_printer", addr);

        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(listenUrl, request, String.class);

        if (response.getStatusCode() == HttpStatus.NO_CONTENT || response.getBody() == null || response.getBody().isBlank()) {
          continue;
        }

        if (response.getStatusCode().is2xxSuccessful()) {
          LogBridge.log("Print job ditemukan untuk [" + (cfg != null ? cfg.getAliasName() : addr) + "]: " + response.getBody());
          JsonNode jsonNode = objectMapper.readTree(response.getBody());
          String type = jsonNode.path("type").asText("");
          if ("Asset".equals(type)) {
            processPrintJobAsset(jsonNode, addr);
          } else if ("Location".equals(type)) {
            processPrintJobLocation(jsonNode, addr);
          } else {
            LogBridge.log("Tipe print job tidak dikenal: " + type);
          }
        }
      }

    } catch (Exception e) {
      if (!e.getMessage().contains("No local printer found") && !e.getMessage().contains("Connection refused")) {
        // LogBridge.log("Polling note: " + e.getMessage());
      }
    }
  }

  /**
   * Smart Sync: Cek dulu GET ke API master printer.
   * Jika sudah ada di database -> Lakukan PUT (Update).
   * Jika belum ada -> Lakukan POST (Create baru) dan simpan ID yang didapat dari server.
   */
  public boolean syncSinglePrinterSmart(PrinterDeviceConfig cfg, String connectionType, boolean isManualTrigger) {
    String printersBaseUrl = configManager.getFullPrintersUrl();
    if (printersBaseUrl == null || printersBaseUrl.trim().isEmpty()) {
      return false;
    }

    try {
      String localPrinter = cfg.getAddress();
      String namePrinter = (cfg.getAliasName() != null && !cfg.getAliasName().trim().isEmpty())
          ? cfg.getAliasName()
          : "Zebra Printer";

      // 1. Cek apakah ID sudah tersimpan, jika belum coba GET cari by local_printer atau by name_printer
      String existingId = cfg.getId();
      if (existingId == null || existingId.trim().isEmpty()) {
        existingId = findPrinterIdFromApi(localPrinter, namePrinter);
        if (existingId != null) {
          cfg.setId(existingId);
          configManager.savePrinters();
        }
      }

      // Siapkan payload (name_printer selalu ambil aliasName terbaru yang diubah user)
      Map<String, Object> body = new HashMap<>();
      body.put("local_printer", localPrinter);
      body.put("name_printer", namePrinter);
      body.put("name", namePrinter); // fallback jika backend memakai key "name"
      body.put("connection_type", connectionType != null ? connectionType : "usb");
      body.put("is_active", cfg.isOnline());
      body.put("is_online", cfg.isOnline());
      body.put("status", cfg.isOnline() ? "online" : "offline");
      body.put("online", cfg.isOnline());

      HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders());

      if (existingId != null && !existingId.trim().isEmpty()) {
        // 2. SUDAH ADA -> Lakukan PUT (UPDATE)
        String updateUrl = printersBaseUrl + "/" + existingId;
        ResponseEntity<String> response = restTemplate.exchange(updateUrl, HttpMethod.PUT, request, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
          // Jika printer sedang ONLINE, panggil juga endpoint heartbeat agar backend memperbarui is_online & last_active
          if (cfg.isOnline()) {
            sendHeartbeat(localPrinter);
          }
          LogBridge.log("Master Printer UPDATE sukses (ID: " + existingId + ") name: " + namePrinter + " status: " + (cfg.isOnline() ? "ONLINE" : "OFFLINE"));
          return true;
        }
      } else {
        // 3. BELUM ADA -> Lakukan POST (CREATE BARU)
        ResponseEntity<String> response = restTemplate.postForEntity(printersBaseUrl, request, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
          try {
            JsonNode resNode = objectMapper.readTree(response.getBody());
            // Tangkap id dari response (bisa "id" langsung, atau resNode.get("data").get("id"))
            String newId = null;
            if (resNode.has("id")) {
              newId = resNode.get("id").asText();
            } else if (resNode.has("data") && resNode.get("data").has("id")) {
              newId = resNode.get("data").get("id").asText();
            }
            if (newId != null && !newId.isEmpty()) {
              cfg.setId(newId);
              configManager.savePrinters();
              LogBridge.log("Master Printer CREATE sukses, dapat ID: " + newId + " untuk [" + namePrinter + "]");
            } else {
              LogBridge.log("Master Printer CREATE sukses untuk [" + namePrinter + "]");
            }
          } catch (Exception ignored) {}
          if (cfg.isOnline()) {
            sendHeartbeat(localPrinter);
          }
          return true;
        }
      }
    } catch (Exception e) {
      if (isManualTrigger) {
        LogBridge.log("Gagal sinkron master printer: " + e.getMessage());
      }
    }
    return false;
  }

  private void sendHeartbeat(String localPrinter) {
    try {
      String printersBaseUrl = configManager.getFullPrintersUrl();
      if (printersBaseUrl == null || printersBaseUrl.trim().isEmpty()) {
        return;
      }
      String heartbeatUrl = printersBaseUrl;
      if (!heartbeatUrl.endsWith("/")) {
        heartbeatUrl += "/";
      }
      heartbeatUrl += "heartbeat";

      Map<String, Object> body = new HashMap<>();
      body.put("local_printer", localPrinter);
      HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders());
      restTemplate.postForEntity(heartbeatUrl, request, String.class);
    } catch (Exception ignored) {}
  }

  private String findPrinterIdFromApi(String localPrinter, String namePrinter) {
    String printersBaseUrl = configManager.getFullPrintersUrl();
    try {
      // Coba query GET /printers?local_printer=...
      String queryUrl = printersBaseUrl + "?local_printer=" + URLEncoder.encode(localPrinter, StandardCharsets.UTF_8);
      HttpHeaders headers = createHeaders();
      HttpEntity<?> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response = restTemplate.exchange(queryUrl, HttpMethod.GET, entity, String.class);
      if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
        JsonNode root = objectMapper.readTree(response.getBody());
        String id = extractIdFromJson(root, localPrinter);
        if (id != null) return id;
      }

      // Jika belum ketemu, coba query GET /printers?name_printer=...
      if (namePrinter != null && !namePrinter.isEmpty()) {
        String queryNameUrl = printersBaseUrl + "?name_printer=" + URLEncoder.encode(namePrinter, StandardCharsets.UTF_8);
        ResponseEntity<String> nameResponse = restTemplate.exchange(queryNameUrl, HttpMethod.GET, entity, String.class);
        if (nameResponse.getStatusCode().is2xxSuccessful() && nameResponse.getBody() != null) {
          JsonNode root = objectMapper.readTree(nameResponse.getBody());
          String id = extractIdFromJson(root, null);
          if (id != null) return id;
        }
      }
    } catch (Exception ignored) {}
    return null;
  }

  private String extractIdFromJson(JsonNode node, String matchAddress) {
    if (node == null) return null;
    // Jika root berupa array
    if (node.isArray() && node.size() > 0) {
      for (JsonNode item : node) {
        if (matchAddress == null || matchAddress.equals(item.path("local_printer").asText())) {
          return item.path("id").asText(null);
        }
      }
      return node.get(0).path("id").asText(null);
    }
    // Jika format data: { "data": [ ... ] }
    if (node.has("data") && node.get("data").isArray() && node.get("data").size() > 0) {
      for (JsonNode item : node.get("data")) {
        if (matchAddress == null || matchAddress.equals(item.path("local_printer").asText())) {
          return item.path("id").asText(null);
        }
      }
      return node.get("data").get(0).path("id").asText(null);
    }
    // Jika format object tunggal { "id": "..." }
    if (node.has("id")) {
      return node.get("id").asText();
    }
    return null;
  }

  public String syncPrintersToExternalApi(boolean isManualTrigger) {
    String printersBaseUrl = configManager.getFullPrintersUrl();
    if (printersBaseUrl == null || printersBaseUrl.trim().isEmpty()) {
      return "URL Master Printer belum diatur.";
    }

    int successCount = 0;
    int totalCount = 0;
    for (PrinterDeviceConfig cfg : configManager.getPrinterMap().values()) {
      if (cfg.isSelectedForSync()) {
        totalCount++;
        String connType = cfg.getAddress().startsWith("tcp:") ? "network" : "usb";
        if (syncSinglePrinterSmart(cfg, connType, isManualTrigger)) {
          successCount++;
        }
      }
    }

    if (totalCount == 0) {
      return "Tidak ada printer yang dipilih untuk disinkron.";
    }
    return "Sinkronisasi selesai: " + successCount + " dari " + totalCount + " printer sukses diproses (Check -> Update/Create).";
  }

  private void processPrintJobAsset(JsonNode jsonNode, String printerAddress) {
    String printJobId = jsonNode.path("print_job_id").asText("");
    try {
      String id = jsonNode.path("id").asText("");
      String qrCode = "{\"type\":\"Asset\",\"qr\":\"" + id + "\"}";
      String rawDate = jsonNode.path("acquired_date").asText("");
      String date = (!rawDate.isEmpty() && !rawDate.equals("null")) ? rawDate.split("T")[0] : "N/A";
      String codeification = jsonNode.path("codeification").asText("-");
      String name = jsonNode.path("name").asText("-");
      String maintenanceGroup = jsonNode.path("maintenance_group").asText("-");
      if (maintenanceGroup.isEmpty() || "null".equals(maintenanceGroup)) {
        maintenanceGroup = "-";
      }
      Boolean kso = jsonNode.path("kso").asBoolean(false);
      int copies = jsonNode.path("copies").asInt(1);

      LogBridge.log("Printing Asset [" + codeification + "] " + name + " | Group: " + maintenanceGroup + " (copies: " + copies + ")");
      zebraPrinterService.printZplFromExternal(printerAddress, qrCode, date, codeification, name, kso, copies, maintenanceGroup);
      LogBridge.log("Sukses mencetak label Asset ID: " + id);

      reportJobStatus(printJobId, "Done Printing", null);
    } catch (Exception e) {
      LogBridge.log("Error cetak asset: " + e.getMessage());
      reportJobStatus(printJobId, "Failed", e.getMessage());
    }
  }

  private void processPrintJobLocation(JsonNode jsonNode, String printerAddress) {
    String printJobId = jsonNode.path("print_job_id").asText("");
    try {
      String id = jsonNode.path("id").asText("");
      if (id.isEmpty()) {
        id = jsonNode.path("qr").path("qr").asText("");
      }
      String qrCode = "{\"type\":\"Location\",\"qr\":\"" + id + "\"}";
      String name = jsonNode.path("name").asText("-");

      LogBridge.log("Printing Location: " + name);
      zebraPrinterService.printZplLocation(printerAddress, qrCode, name);
      LogBridge.log("Sukses mencetak label Location ID: " + id);

      reportJobStatus(printJobId, "Done Printing", null);
    } catch (Exception e) {
      LogBridge.log("Error cetak location: " + e.getMessage());
      reportJobStatus(printJobId, "Failed", e.getMessage());
    }
  }

  private void reportJobStatus(String printJobId, String status, String errorMessage) {
    if (printJobId == null || printJobId.isEmpty()) {
      return;
    }
    String updateUrl = configManager.getFullUpdateJobUrl();
    if (updateUrl == null || updateUrl.trim().isEmpty()) {
      return;
    }
    try {
      Map<String, Object> body = new HashMap<>();
      body.put("id", printJobId);
      body.put("status", status);
      if (errorMessage != null && !errorMessage.isEmpty()) {
        body.put("error_message", errorMessage);
      }
      HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), createHeaders());
      restTemplate.postForEntity(updateUrl, request, String.class);
      LogBridge.log("Status update dikirim ke server [" + status + "]: " + printJobId);
    } catch (Exception e) {
      LogBridge.log("Gagal mengirim status update ke server: " + e.getMessage());
    }
  }
}
