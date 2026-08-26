package com.unicconnect.rmi.server;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan
@ConfigurationProperties(prefix = "rmi")
public class RmiServerProperties {
    private int port = 1099;
    private String hostname = "localhost";
    private String userBinding = "UserService";
    private String attendanceBinding = "AttendanceService";
    private String timetableBinding = "TimetableService";
    private String sharedSecret = "uni-dev-secret-change-me";

    public int getPort() { return port; }
    public void setPort(int v) { this.port = v; }
    public String getHostname() { return hostname; }
    public void setHostname(String v) { this.hostname = v; }
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
