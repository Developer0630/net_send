package com.netsend.config;

import com.netsend.model.GroupConfig;
import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    private String displayName = System.getProperty("user.name", "NetSend User");
    private String interfaceAddress = "";
    private String broadcastAddress = "255.255.255.255";
    private int listenPort = 45454;
    private boolean notificationsEnabled = true;
    private List<GroupConfig> groups = new ArrayList<>(List.of(new GroupConfig()));

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getInterfaceAddress() { return interfaceAddress; }
    public void setInterfaceAddress(String interfaceAddress) { this.interfaceAddress = interfaceAddress; }
    public String getBroadcastAddress() { return broadcastAddress; }
    public void setBroadcastAddress(String broadcastAddress) { this.broadcastAddress = broadcastAddress; }
    public int getListenPort() { return listenPort; }
    public void setListenPort(int listenPort) { this.listenPort = listenPort; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
    public List<GroupConfig> getGroups() { return groups; }
    public void setGroups(List<GroupConfig> groups) { this.groups = groups == null ? new ArrayList<>() : groups; }
}
