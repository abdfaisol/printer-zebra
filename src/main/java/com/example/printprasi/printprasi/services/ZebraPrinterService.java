package com.example.printprasi.printprasi.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.zebra.sdk.comm.Connection;
import com.zebra.sdk.comm.ConnectionException;
import com.zebra.sdk.comm.TcpConnection;
import com.zebra.sdk.comm.UsbConnection;
import com.zebra.sdk.printer.ZebraPrinter;
import com.zebra.sdk.printer.ZebraPrinterFactory;
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException;
import com.zebra.sdk.printer.discovery.DiscoveredPrinter;
import com.zebra.sdk.printer.discovery.DiscoveredPrinterDriver;
import com.zebra.sdk.printer.discovery.DiscoveredUsbPrinter;
import com.zebra.sdk.printer.discovery.DiscoveryException;
import com.zebra.sdk.printer.discovery.DiscoveryHandler;
import com.zebra.sdk.printer.discovery.NetworkDiscoverer;
import com.zebra.sdk.printer.discovery.UsbDiscoverer;
import com.zebra.sdk.printer.discovery.ZebraPrinterFilter;

@Service
public class ZebraPrinterService {

  public List<com.example.printprasi.printprasi.models.DiscoveredPrinterInfo> findAllDiscoveredPrinters() {
    List<com.example.printprasi.printprasi.models.DiscoveredPrinterInfo> result = new ArrayList<>();
    
    // 1. Scan via Zebra UsbDiscoverer (Native USB)
    try {
      for (DiscoveredUsbPrinter usbPrinter : UsbDiscoverer.getZebraUsbPrinters(new ZebraPrinterFilter())) {
        if (usbPrinter != null && usbPrinter.address != null && !usbPrinter.address.trim().isEmpty()) {
          Map<String, String> dataMap = usbPrinter.getDiscoveryDataMap();
          String name = (dataMap != null && dataMap.containsKey("MODEL"))
              ? dataMap.get("MODEL")
              : "Zebra USB (" + usbPrinter.address + ")";
          result.add(new com.example.printprasi.printprasi.models.DiscoveredPrinterInfo(usbPrinter.address, name, "usb"));
        }
      }
    } catch (Throwable t) {
      System.err.println("Note: Native UsbDiscoverer scan: " + t.getMessage());
    }

    // 2. Scan via Zebra Windows Driver Printers (ZDriverAdapter)
    try {
      for (DiscoveredPrinterDriver driverPrinter : UsbDiscoverer.getZebraDriverPrinters()) {
        if (driverPrinter != null) {
          String addr = driverPrinter.printerName;
          boolean alreadyExists = false;
          for (com.example.printprasi.printprasi.models.DiscoveredPrinterInfo existing : result) {
            if (existing.getAddress().equalsIgnoreCase(addr)) {
              alreadyExists = true;
              break;
            }
          }
          if (!alreadyExists) {
            result.add(new com.example.printprasi.printprasi.models.DiscoveredPrinterInfo(addr, driverPrinter.printerName, "usb"));
          }
        }
      }
    } catch (Throwable t) {
      System.err.println("Note: DriverPrinters scan: " + t.getMessage());
    }

    return result;
  }

  public List<DiscoveredUsbPrinter> findAllLocalPrinters() {
    List<DiscoveredUsbPrinter> printers = new ArrayList<>();
    try {
      for (DiscoveredUsbPrinter usbPrinter : UsbDiscoverer.getZebraUsbPrinters(new ZebraPrinterFilter())) {
        printers.add(usbPrinter);
      }
    } catch (Exception ignored) {}
    return printers;
  }

  public Map<String, String> findLocalPrinter() {
    try {
      Map<String, String> printerData = new HashMap<>();
      for (DiscoveredPrinterDriver printer : UsbDiscoverer.getZebraDriverPrinters()) {
        // return printer.toString();
        printerData.put("printer", printer.toString());
      }

      for (DiscoveredUsbPrinter usbPrinter : UsbDiscoverer.getZebraUsbPrinters(new ZebraPrinterFilter())) {
        // return usbPrinter.toString();
        printerData.put("address", usbPrinter.address);
      }

      if (printerData.isEmpty()) {
        throw new RuntimeException("No local printer found.");
      }

      return printerData;
    } catch (ConnectionException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public Map<String, String> findNetworkPrinter() {
    Map<String, String> printerData = new HashMap<>();
    DiscoveryHandler discoveryHandler = new DiscoveryHandler() {
      List<DiscoveredPrinter> printers = new ArrayList<>();

      public void foundPrinter(DiscoveredPrinter printer) {
        printers.add(printer);
      }

      public void discoveryFinished() {
        for (DiscoveredPrinter printer : printers) {
          printerData.put(printer.address, printer.toString());
          System.out.println(printer);
        }
        System.out.println("Discovered " + printers.size() + " printers.");
      }

      public void discoveryError(String message) {
        System.out.println("An error occurred during discovery: " + message);
      }
    };

    try {
      System.out.println("Starting printer discovery.");
      NetworkDiscoverer.findPrinters(discoveryHandler);
    } catch (DiscoveryException e) {
      throw new RuntimeException(e.getMessage(), e);
    }

    if (printerData.isEmpty()) {
      throw new RuntimeException("No Zebra printer found");
    }

    return printerData;
  }

  public void printLabelExample(String printAddress) throws ConnectionException {
    Connection connection = createConnection(printAddress);
    try {
      connection.open();

      String zplData = "CT~~CD,~CC^~CT~\r\n" + //
          "^XA\r\n" + //
          "~TA000\r\n" + //
          "~JSN\r\n" + //
          "^LT0\r\n" + //
          "^MNW\r\n" + //
          "^MTT\r\n" + //
          "^PON\r\n" + //
          "^PMN\r\n" + //
          "^LH0,0\r\n" + //
          "^JMA\r\n" + //
          "^PR4,4\r\n" + //
          "~SD15\r\n" + //
          "^JUS\r\n" + //
          "^LRN\r\n" + //
          "^CI27\r\n" + //
          "^PA0,1,1,0\r\n" + //
          "^XZ\r\n" + //
          "^XA\r\n" + //
          "^MMT\r\n" + //
          "^PW400\r\n" + //
          "^LL200\r\n" + //
          "^LS0\r\n" + //
          "^FT14,173^BQN,2,6\r\n" + //
          "^FH\\^FDLA,FL6521000001^FS\r\n" + //
          "^FT14,186^A0N,20,18^FH\\^CI28^FDLaptop HP 14-AC181TU-SCG6126RB7^FS^CI27\r\n" + //
          "^FT158,103^A0N,20,20^FH\\^CI28^FDTue Dec 20 2022^FS^CI27\r\n" + //
          "^FT158,129^A0N,21,18^FH\\^CI28^FDIT/LAPTOP/Non/2022/0000164^FS^CI27\r\n" + //
          "^FT310,36^A0N,19,18^FH\\^CI28^FDNon-KSO^FS^CI27\r\n" + //
          "^PQ1,0,1,Y\r\n" + //
          "^XZ\r\n" + //
          "";

      connection.write(zplData.getBytes());
    } catch (ConnectionException e) {
      throw new RuntimeException(e.getMessage(), e);
    } finally {
      connection.close();
    }
  }

  public void printImageExample(String printAddress) throws ConnectionException {
    Connection connection = new UsbConnection(printAddress);
    InputStream imageStream = null;
    java.nio.file.Path tempFile = null;
    try {
      connection.open();
      ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
      int x = 0;
      int y = 0;

      imageStream = getClass().getResourceAsStream("/static/sample.png");
      if (imageStream == null) {
        throw new IOException("Image not found in resources");
      }

      tempFile = Files.createTempFile("sample", ".png");
      Files.copy(imageStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      printer.printImage(tempFile.toString(), x, y);
    } catch (ConnectionException | ZebraPrinterLanguageUnknownException | IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (ConnectionException e) {
          e.printStackTrace();
        }
      }
      if (imageStream != null) {
        try {
          imageStream.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      if (tempFile != null) {
        try {
          Files.delete(tempFile);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void printImageFromExternal(String printAddress, MultipartFile image) throws ConnectionException {
    Connection connection = new UsbConnection(printAddress);
    java.nio.file.Path tempFile = null;
    try {
      connection.open();
      ZebraPrinter printer = ZebraPrinterFactory.getInstance(connection);
      int x = 0;
      int y = 0;

      tempFile = Files.createTempFile("mustprint", ".png");
      Files.copy(image.getInputStream(), tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      printer.printImage(tempFile.toString(), x, y);
    } catch (ConnectionException | ZebraPrinterLanguageUnknownException | IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
        } catch (ConnectionException e) {
          e.printStackTrace();
        }
      }
      if (tempFile != null) {
        try {
          Files.delete(tempFile);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public Connection createConnection(String printAddress) throws ConnectionException {
    if (printAddress != null && printAddress.startsWith("tcp://")) {
      String hostPort = printAddress.substring("tcp://".length());
      String[] parts = hostPort.split(":");
      String host = parts[0];
      int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9100;
      return new TcpConnection(host, port);
    }
    return new UsbConnection(printAddress);
  }

  public void printZplFromExternal(String printAddress, String qrCode, String date, String codeification, String name, Boolean kso)
      throws ConnectionException {
    printZplFromExternal(printAddress, qrCode, date, codeification, name, kso, 1);
  }

  public void printZplFromExternal(String printAddress, String qrCode, String date, String codeification, String name, Boolean kso, int copies)
      throws ConnectionException {
    printZplFromExternal(printAddress, qrCode, date, codeification, name, kso, copies, "-");
  }

  public void printZplFromExternal(String printAddress, String qrCode, String date, String codeification, String name, Boolean kso, int copies, String maintenanceGroup)
      throws ConnectionException {
    Connection connection = createConnection(printAddress);
    try {
      connection.open();

      int copyCount = copies > 0 ? copies : 1;
      String codeString = (codeification != null && !codeification.isEmpty()) ? codeification : "-";
      String nameString = (name != null && !name.isEmpty()) ? name : "-";
      String maintGroupString = (maintenanceGroup != null && !maintenanceGroup.isEmpty()) ? maintenanceGroup : "-";

      String zplData = "CT~~CD,~CC^~CT~\r\n" + //
          "^XA\r\n" + //
          "~TA000\r\n" + //
          "~JSN\r\n" + //
          "^LT0\r\n" + //
          "^MNW\r\n" + //
          "^MTT\r\n" + //
          "^PON\r\n" + //
          "^PMN\r\n" + //
          "^LH0,0\r\n" + //
          "^JMA\r\n" + //
          "^PR4,4\r\n" + //
          "~SD15\r\n" + //
          "^JUS\r\n" + //
          "^LRN\r\n" + //
          "^CI27\r\n" + //
          "^PA0,1,1,0\r\n" + //
          "^XZ\r\n" + //
          "^XA\r\n" + //
          "^MMT\r\n" + //
          "^PW400\r\n" + //
          "^LL180\r\n" + //
          "^LS0\r\n" + //
          "^FO16,24^BQN,2,3\r\n" + //
          "^FH\\^FDLA," + qrCode + "^FS\r\n" + //
          "^FO140,22^A0N,22,20^FB250,2,2,L,0^FH\\^CI28^FD" + nameString + "^FS^CI27\r\n" + //
          "^FO140,72^A0N,15,14^FH\\^CI28^FDCode:^FS^CI27\r\n" + //
          "^FO140,90^A0N,18,16^FH\\^CI28^FD" + codeString + "^FS^CI27\r\n" + //
          "^FO140,120^A0N,15,14^FH\\^CI28^FDMaint. Group:^FS^CI27\r\n" + //
          "^FO140,138^A0N,18,16^FH\\^CI28^FD" + maintGroupString + "^FS^CI27\r\n" + //
          "^PQ" + copyCount + ",0,1,Y\r\n" + //
          "^XZ\r\n" + //
          "";

      connection.write(zplData.getBytes());
    } catch (ConnectionException e) {
      throw new RuntimeException(e.getMessage(), e);
    } finally {
      try {
        connection.close();
      } catch (ConnectionException e) {
        // Log the exception or handle it as needed
        throw new RuntimeException(e.getMessage(), e);
      }
    }
  }

  public void printZplLocation(String printAddress, String qrCode, String name) throws ConnectionException {
    Connection connection = createConnection(printAddress);
    try {
      connection.open();

      String zplData = "CT~~CD,~CC^~CT~\r\n" + //
          "^XA\r\n" + //
          "~TA000\r\n" + //
          "~JSN\r\n" + //
          "^LT0\r\n" + //
          "^MNW\r\n" + //
          "^MTT\r\n" + //
          "^PON\r\n" + //
          "^PMN\r\n" + //
          "^LH0,0\r\n" + //
          "^JMA\r\n" + //
          "^PR4,4\r\n" + //
          "~SD15\r\n" + //
          "^JUS\r\n" + //
          "^LRN\r\n" + //
          "^CI27\r\n" + //
          "^PA0,1,1,0\r\n" + //
          "^XZ\r\n" + //
          "^XA\r\n" + //
          "^MMT\r\n" + //
          "^PW799\r\n" + //
          "^LL599\r\n" + //
          "^LS0\r\n" + //
          "^FT23,98^A0N,74,74^FH\\^CI28^FDSCAN FOR QR LOCATION^FS^CI27\r\n" + //
          "^FT223,531^A0N,80,81^FH\\^CI28^FD" + name + "^FS^CI27\r\n" + //
          "^FT275,400^BQN,2,7\r\n" + //
          "^FH\\^FDLA," + qrCode + "^FS\r\n" + //
          "^PQ1,0,1,Y\r\n" + //
          "^XZ\r\n";
      connection.write(zplData.getBytes());
    } catch (ConnectionException e) {
      throw new RuntimeException(e.getMessage(), e);
    } finally {
      try {
        connection.close();
      } catch (ConnectionException e) {
        // Log the exception or handle it as needed
        throw new RuntimeException(e.getMessage(), e);
      }
    }
  }
}
