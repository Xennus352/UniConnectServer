package com.unicconnect.ops;

import com.unicconnect.rmi.client.RmiClientConfig.AttendanceRmiClient;
import com.unicconnect.rmi.client.RmiClientConfig.TimetableRmiClient;
import com.unicconnect.rmi.client.RmiClientConfig.UserRmiClient;
import com.unicconnect.rmi.client.RmiClientProperties;
import com.unicconnect.security.CallerContextFactory;
import com.unicconnect.service.AttendanceCalculationService;
import com.unicconnect.service.AttendanceService;
import com.unicconnect.service.ClassScheduleService;
import com.unicconnect.service.TimetableGenerationService;
import com.unicconnect.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hybrid router. With {@code rmi.enabled=false} (default) every selected
 * operation keeps its original in-process path; with {@code true} the same
 * controller code transparently crosses the JRMP boundary instead.
 */
@Configuration
public class RmiRoutingConfig {

    // ---------- USER CRUD ----------

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "false", matchIfMissing = true)
    public UserOperations localUserOperations(UserService service) {
        return new UserRouting.Local(service);
    }

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "true")
    public UserOperations rmiUserOperations(UserRmiClient client, CallerContextFactory ctx) {
        return new UserRouting.Remote(client, ctx);
    }

    // ---------- ATTENDANCE ----------

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "false", matchIfMissing = true)
    public AttendanceOperations localAttendanceOperations(AttendanceService service,
    AttendanceCalculationService calculationService) {
        return new AttendanceRouting.Local(service, calculationService);
    }

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "true")
    public AttendanceOperations rmiAttendanceOperations(AttendanceRmiClient client, CallerContextFactory ctx) {
        return new AttendanceRouting.Remote(client, ctx);
    }

    // ---------- TIMETABLE QUERY ----------

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "false", matchIfMissing = true)
    public TimetableQueryOperations localTimetableQueryOperations(ClassScheduleService service) {
        return new TimetableQueryRouting.Local(service);
    }

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "true")
    public TimetableQueryOperations rmiTimetableQueryOperations(TimetableRmiClient client, CallerContextFactory ctx) {
        return new TimetableQueryRouting.Remote(client, ctx);
    }

    // ---------- TIMETABLE GENERATION ----------

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "false", matchIfMissing = true)
    public TimetableGenerationOperations localGenerationOperations(TimetableGenerationService service) {
        return new TimetableGenerationRouting.Local(service);
    }

    @Bean
    @ConditionalOnProperty(name = "rmi.enabled", havingValue = "true")
    public TimetableGenerationOperations rmiGenerationOperations(TimetableRmiClient client, CallerContextFactory ctx) {
        return new TimetableGenerationRouting.Remote(client, ctx);
    }
}
