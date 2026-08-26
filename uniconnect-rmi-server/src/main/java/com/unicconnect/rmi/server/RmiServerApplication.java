package com.unicconnect.rmi.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Standalone RMI Server JVM. Shares uniconnect-core with the API tier and
 * connects to the SAME PostgreSQL database; it never calls back into the
 * Spring Boot HTTP application.
 */
@SpringBootApplication(
        scanBasePackages = "com.unicconnect")
@EntityScan(basePackages = "com.unicconnect.entity")
@EnableJpaRepositories(basePackages = "com.unicconnect.repository")
public class RmiServerApplication {

    public static void main(String[] args) throws Exception {
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
    }
}
