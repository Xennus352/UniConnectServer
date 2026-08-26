const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/RmiServerApplication.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace(`    public static void main(String[] args) {
        SpringApplication.run(RmiServerApplication.class, args);
        System.out.println("[RMI] UniConnect RMI Server is up - registry on port "
                + System.getProperty("rmi.port", "1099"));
    }`,
`    public static void main(String[] args) throws Exception {
        var ctx = SpringApplication.run(RmiServerApplication.class, args);
        System.out.println("[RMI] UniConnect RMI Server is up - registry on port "
                + System.getProperty("rmi.port", "1099"));

        // With no web server, Boot would exit right after main() and the
        // shutdown hook would unbind everything. Keep the JVM (and therefore
        // the registry + exported facades) alive until the process is killed;
        // SIGTERM/Stop-Process still runs the normal @PreDestroy cleanup.
        java.util.concurrent.CountDownLatch keepAlive = new java.util.concurrent.CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(keepAlive::countDown));
        keepAlive.await();
        ctx.close();
    }`);
fs.writeFileSync(p,c);console.log('keep-alive added');
