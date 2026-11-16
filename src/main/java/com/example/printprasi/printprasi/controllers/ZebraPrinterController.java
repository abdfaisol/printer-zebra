package com.example.printprasi.printprasi.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.printprasi.printprasi.helpers.ResponseFormatter;
import com.example.printprasi.printprasi.requests.PrintAddressReequest;
import com.example.printprasi.printprasi.requests.PrintZplRequest;
import com.example.printprasi.printprasi.requests.PrintZplWithoutAddressRequest;
import com.example.printprasi.printprasi.services.ZebraPrinterService;

@RestController
@RequestMapping("/api/zebra-printer")
public class ZebraPrinterController {
  private final ZebraPrinterService zebraPrinterService;

  @Autowired
  public ZebraPrinterController(ZebraPrinterService zebraPrinterService) {
    this.zebraPrinterService = zebraPrinterService;
  }

  @GetMapping("/find-local-printer")
  public ResponseEntity<?> findLocalPrinter() {
    try {
      Map<String, String> zebraPrinter = zebraPrinterService.findLocalPrinter();
      return ResponseFormatter.success(zebraPrinter, "Local printer found", null, HttpStatus.OK.value(),
          HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @GetMapping("/find-network-printer")
  public ResponseEntity<?> findNetworkPrinter() {
    try {
      Map<String, String> zebraPrinter = zebraPrinterService.findNetworkPrinter();
      return ResponseFormatter.success(zebraPrinter, "Network printer found", null, HttpStatus.OK.value(),
          HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @PostMapping("/print-label-example")
  public ResponseEntity<?> printLabelExample(@RequestBody PrintAddressReequest printAddress) {
    try {
      zebraPrinterService.printLabelExample(printAddress.getPrintAddress());
      return ResponseFormatter.success(null, "Label printed", null, HttpStatus.OK.value(), HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @PostMapping("/print-image-example")
  public ResponseEntity<?> printImageExample(@RequestBody PrintAddressReequest printAddress) {
    try {
      zebraPrinterService.printImageExample(printAddress.getPrintAddress());
      return ResponseFormatter.success(null, "Image printed", null, HttpStatus.OK.value(), HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @PostMapping("/print-image")
  public ResponseEntity<?> printImage(@RequestParam("printAddress") String printAddress,
      @RequestParam("image") MultipartFile image) {
    try {
      zebraPrinterService.printImageFromExternal(printAddress, image);
      return ResponseFormatter.success(null, "Image printed", null, HttpStatus.OK.value(), HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @PostMapping("/print-zpl")
  public ResponseEntity<?> printZplFromExternal(@RequestBody PrintZplRequest printZplRequest) {
    if (printZplRequest.getPrintAddress() == null || printZplRequest.getPrintAddress().isEmpty() ||
        printZplRequest.getQrCode() == null || printZplRequest.getQrCode().isEmpty() ||
        printZplRequest.getDate() == null || printZplRequest.getDate().isEmpty() ||
        printZplRequest.getCodeification() == null || printZplRequest.getCodeification().isEmpty() ||
        printZplRequest.getName() == null || printZplRequest.getName().isEmpty()) {

      return ResponseFormatter.error(null, "Invalid request parameters", null, HttpStatus.BAD_REQUEST.value(),
          HttpStatus.BAD_REQUEST.value());
    }
    try {
      zebraPrinterService.printZplFromExternal(printZplRequest.getPrintAddress(), printZplRequest.getQrCode(),
          printZplRequest.getDate(), printZplRequest.getCodeification(), printZplRequest.getName(), false);
      return ResponseFormatter.success(null, "ZPL printed", null, HttpStatus.OK.value(), HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @PostMapping("/print-zpl/without-address")
  public ResponseEntity<?> printZplWithoutAddress(@RequestParam("printAddress") String printAddress,
      @RequestBody PrintZplWithoutAddressRequest printZplRequest) {
    if (printZplRequest.getQrCode() == null || printZplRequest.getQrCode().isEmpty() ||
        printZplRequest.getDate() == null || printZplRequest.getDate().isEmpty() ||
        printZplRequest.getCodeification() == null || printZplRequest.getCodeification().isEmpty() ||
        printZplRequest.getName() == null || printZplRequest.getName().isEmpty()) {

      return ResponseFormatter.error(null, "Invalid request parameters", null, HttpStatus.BAD_REQUEST.value(),
          HttpStatus.BAD_REQUEST.value());
    }
    try {
      zebraPrinterService.printZplFromExternal(printAddress, printZplRequest.getQrCode(),
          printZplRequest.getDate(), printZplRequest.getCodeification(), printZplRequest.getName(), false);
      return ResponseFormatter.success(null, "ZPL printed", null, HttpStatus.OK.value(), HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }

  @PostMapping("/print-zpl/location")
  public ResponseEntity<?> printZplLocation(@RequestBody PrintZplRequest printZplRequest) {
    if (printZplRequest.getPrintAddress() == null || printZplRequest.getPrintAddress().isEmpty() ||
        printZplRequest.getQrCode() == null || printZplRequest.getQrCode().isEmpty() ||
        printZplRequest.getDate() == null || printZplRequest.getDate().isEmpty() ||
        printZplRequest.getCodeification() == null || printZplRequest.getCodeification().isEmpty() ||
        printZplRequest.getName() == null || printZplRequest.getName().isEmpty()) {

      return ResponseFormatter.error(null, "Invalid request parameters", null, HttpStatus.BAD_REQUEST.value(),
          HttpStatus.BAD_REQUEST.value());
    }
    try {
      zebraPrinterService.printZplLocation(printZplRequest.getPrintAddress(), printZplRequest.getQrCode(),
          printZplRequest.getName());
      return ResponseFormatter.success(null, "ZPL printed", null, HttpStatus.OK.value(), HttpStatus.OK.value());
    } catch (Exception e) {
      return ResponseFormatter.error(null, e.getMessage(), null, 500, 500);
    }
  }
}
