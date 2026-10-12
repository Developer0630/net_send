package com.netsend.model;

public class GroupConfig {
    private String name = "GROUP_CLASS";
    private String description = "Default multicast group";
    private String address = "239.10.10.10";
    private int port = 45454;
    private boolean joined = true;

    public GroupConfig() {}
    public GroupConfig(String name, String description, String address, int port) {
        this.name = name; this.description = description; this.address = address; this.port = port;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isJoined() { return joined; }
    public void setJoined(boolean joined) { this.joined = joined; }
}
