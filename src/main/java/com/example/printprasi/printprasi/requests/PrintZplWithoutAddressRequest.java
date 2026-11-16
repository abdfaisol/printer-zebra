package com.example.printprasi.printprasi.requests;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class PrintZplWithoutAddressRequest {
  private String qrCode;
  private String date;
  private String codeification;
  private String name;

  public PrintZplWithoutAddressRequest(String qrCode, String date, String codeification, String name) {
    this.qrCode = qrCode;
    this.date = date;
    this.codeification = codeification;
    this.name = name;
  }
  
}
