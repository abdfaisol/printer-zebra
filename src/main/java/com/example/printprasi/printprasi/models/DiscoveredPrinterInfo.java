package com.example.printprasi.printprasi.models;

import java.io.Serializable;

public class DiscoveredPrinterInfo implements Serializable {
    private String address;
    private String name;
    private String connectionType; // "usb" or "network"

    public DiscoveredPrinterInfo() {}

    public DiscoveredPrinterInfo(String address, String name, String connectionType) {
        this.address = address;
        this.name = name;
        this.connectionType = connectionType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getConnectionType() {
        return connectionType;
    }

    public void setConnectionType(String connectionType) {
        this.connectionType = connectionType;
    }
}
