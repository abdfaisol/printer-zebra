package com.example.printprasi.printprasi.requests;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class PrintAddressReequest {
  
  private String printAddress;

  public PrintAddressReequest(String printAddress) {
    this.printAddress = printAddress;
  }
}
