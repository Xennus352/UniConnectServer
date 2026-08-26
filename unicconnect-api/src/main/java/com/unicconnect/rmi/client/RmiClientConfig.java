package com.unicconnect.rmi.client;

import com.unicconnect.rmi.remote.AttendanceRemote;
import com.unicconnect.rmi.remote.TimetableRemote;
import com.unicconnect.rmi.remote.UserRemote;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One cached-stub client bean per remote domain. */
@Configuration
@EnableConfigurationProperties(RmiClientProperties.class)
public class RmiClientConfig {

    @Bean
    public UserRmiClient userRmiClient(RmiClientProperties p) {
        return new UserRmiClient(p);
    }

    @Bean
    public AttendanceRmiClient attendanceRmiClient(RmiClientProperties p) {
        return new AttendanceRmiClient(p);
    }

    @Bean
    public TimetableRmiClient timetableRmiClient(RmiClientProperties p) {
        return new TimetableRmiClient(p);
    }

    public static class UserRmiClient extends RmiStubCache<UserRemote> {
        public UserRmiClient(RmiClientProperties p) { super(p.getHost(), p.getPort(), p.getUserBinding(), UserRemote.class); }
    }

    public static class AttendanceRmiClient extends RmiStubCache<AttendanceRemote> {
        public AttendanceRmiClient(RmiClientProperties p) { super(p.getHost(), p.getPort(), p.getAttendanceBinding(), AttendanceRemote.class); }
    }

    public static class TimetableRmiClient extends RmiStubCache<TimetableRemote> {
        public TimetableRmiClient(RmiClientProperties p) { super(p.getHost(), p.getPort(), p.getTimetableBinding(), TimetableRemote.class); }
    }
}
