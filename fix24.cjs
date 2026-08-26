const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/RmiExporter.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace(`        bind(props.getUserBinding(), userRemoteFacade);
        bind(props.getAttendanceBinding(), attendanceRemoteFacade);
        bind(props.getTimetableBinding(), timetableRemoteFacade);`,
`        bind(props.getUserBinding(), userRemoteFacade);
        bind(props.getAttendanceBinding(), attendanceRemoteFacade);
        bind(props.getTimetableBinding(), timetableRemoteFacade);
        // keep strong references to the exported stubs so DGC cannot reclaim them
        exportedStubs = new Remote[]{userStub, attendanceStub, timetableStub};`);
c=c.replace(`    private void bind(String name, Remote stub) throws RemoteException {
        registry.rebind(name, stub);`,
`    private UserRemote userStub;
    private AttendanceRemote attendanceStub;
    private TimetableRemote timetableStub;
    private Remote[] exportedStubs;

    private void bind(String name, Object facade) throws RemoteException {
        Remote stub = java.rmi.server.UnicastRemoteObject.exportObject(facade, 0);
        if (facade instanceof UserRemote u) userStub = u;
        if (facade instanceof AttendanceRemote a) attendanceStub = a;
        if (facade instanceof TimetableRemote t) timetableStub = t;
        registry.rebind(name, stub);`);
c=c.replace(`        unexport(userRemoteFacade);
        unexport(attendanceRemoteFacade);
        unexport(timetableRemoteFacade);`,
`        for (Remote s : exportedStubs == null ? new Remote[0] : exportedStubs) unexport(s);`);
fs.writeFileSync(p,c);console.log('exporter fixed');
