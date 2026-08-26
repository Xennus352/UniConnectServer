package com.unicconnect.rmi.server;

import com.unicconnect.rmi.remote.AttendanceRemote;
import com.unicconnect.rmi.remote.TimetableRemote;
import com.unicconnect.rmi.remote.UserRemote;
import com.unicconnect.rmi.server.facade.AttendanceRemoteFacade;
import com.unicconnect.rmi.server.facade.TimetableRemoteFacade;
import com.unicconnect.rmi.server.facade.UserRemoteFacade;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Starts the EMBEDDED RMI registry (no separate rmiregistry process) inside
 * this JVM and binds the three remote facades:
 *
 *   rmi://host:1099/UserService
 *   rmi://host:1099/AttendanceService
 *   rmi://host:1099/TimetableService
 */
@Component
public class RmiExporter {

    private static final Logger log = LoggerFactory.getLogger(RmiExporter.class);

    private final RmiServerProperties props;
    private final UserRemoteFacade userRemoteFacade;
    private final AttendanceRemoteFacade attendanceRemoteFacade;
    private final TimetableRemoteFacade timetableRemoteFacade;
    private Registry registry;

    public RmiExporter(RmiServerProperties props,
                       UserRemoteFacade userRemoteFacade,
                       AttendanceRemoteFacade attendanceRemoteFacade,
                       TimetableRemoteFacade timetableRemoteFacade) {
        this.props = props;
        this.userRemoteFacade = userRemoteFacade;
        this.attendanceRemoteFacade = attendanceRemoteFacade;
        this.timetableRemoteFacade = timetableRemoteFacade;
    }

    @PostConstruct
    void exportAndBind() throws RemoteException {
        System.setProperty("java.rmi.server.hostname", props.getHostname());

        try {
            registry = LocateRegistry.createRegistry(props.getPort());
            log.info("[RMI] embedded registry created on port {}", props.getPort());
        } catch (RemoteException e) {
            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                registry = LocateRegistry.getRegistry(props.getPort());
                log.info("[RMI] reusing existing registry on port {}", props.getPort());
            } else {
                throw e;
            }
        }

        bind(props.getUserBinding(), userRemoteFacade);
        bind(props.getAttendanceBinding(), attendanceRemoteFacade);
        bind(props.getTimetableBinding(), timetableRemoteFacade);
        // keep strong references to the exported stubs so DGC cannot reclaim them
        exportedStubs = new Remote[]{userStub, attendanceStub, timetableStub};
    }

    private UserRemote userStub;
    private AttendanceRemote attendanceStub;
    private TimetableRemote timetableStub;
    private Remote[] exportedStubs;

    private void bind(String name, Remote facade) throws RemoteException {
        Remote stub = java.rmi.server.UnicastRemoteObject.exportObject(facade, 0);
        if (facade instanceof UserRemote u) userStub = u;
        if (facade instanceof AttendanceRemote a) attendanceStub = a;
        if (facade instanceof TimetableRemote t) timetableStub = t;
        registry.rebind(name, stub);
        log.info("[RMI] bound {} -> rmi://{}:{}/{}", name,
                props.getHostname(), props.getPort(), name);
    }

    @PreDestroy
    void unbindAndUnexport() {
        String[] names = {props.getUserBinding(), props.getAttendanceBinding(), props.getTimetableBinding()};
        for (String n : names) {
            try { registry.unbind(n); } catch (Exception ignored) {}
        }
        for (Remote s : exportedStubs == null ? new Remote[0] : exportedStubs) unexport(s);
        log.info("[RMI] all bindings removed - server shut down cleanly");
    }

    private void unexport(Remote r) {
        try { java.rmi.server.UnicastRemoteObject.unexportObject(r, true); } catch (Exception ignored) {}
    }
}
