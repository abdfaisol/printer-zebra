package com.example.printprasi.printprasi.models;

import java.io.Serializable;

public class PrinterDeviceConfig implements Serializable {
    private String id; // ID dari database backend
    private String address;
    private String aliasName;
    private boolean selectedForSync = true;
    private boolean online;

    public PrinterDeviceConfig() {}

    public PrinterDeviceConfig(String address, String aliasName, boolean online) {
        this.address = address;
        this.aliasName = aliasName;
        this.online = online;
        this.selectedForSync = true;
    }

    public PrinterDeviceConfig(String address, String aliasName, boolean selectedForSync, boolean online) {
        this.address = address;
        this.aliasName = aliasName;
        this.selectedForSync = selectedForSync;
        this.online = online;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getAliasName() {
        return aliasName;
    }

    public void setAliasName(String aliasName) {
        this.aliasName = aliasName;
    }

    public boolean isSelectedForSync() {
        return selectedForSync;
    }

    public void setSelectedForSync(boolean selectedForSync) {
        this.selectedForSync = selectedForSync;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
