package com.unicconnect.rmi.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hybrid-routing configuration. {@code enabled=false} keeps every selected
 * operation on its original in-process service path.
 */
@ConfigurationProperties(prefix = "rmi")
public class RmiClientProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 1099;
    private String userBinding = "UserService";
    private String attendanceBinding = "AttendanceService";
    private String timetableBinding = "TimetableService";
    /** HMAC secret shared with the RMI Server (env RMI_SHARED_SECRET). */
    private String sharedSecret = "uni-dev-secret-change-me";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }
    public int getPort() { return port; }
    public void setPort(int v) { this.port = v; }
    public String getUserBinding() { return userBinding; }
    public void setUserBinding(String v) { this.userBinding = v; }
    public String getAttendanceBinding() { return attendanceBinding; }
    public void setAttendanceBinding(String v) { this.attendanceBinding = v; }
    public String getTimetableBinding() { return timetableBinding; }
    public void setTimetableBinding(String v) { this.timetableBinding = v; }
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String v) { this.sharedSecret = v; }

    public byte[] secretBytes() { return sharedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
}
