package com.example.printprasi.printprasi.requests;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PrintZplRequest {
  private String qrCode;
  private String printAddress;
  private String date;
  private String codeification;
  private String name;

  public PrintZplRequest(String qrCode, String printAddress, String date, String codeification, String name) {
    this.qrCode = qrCode;
    this.printAddress = printAddress;
    this.date = date;
    this.codeification = codeification;
    this.name = name;
  }
}
