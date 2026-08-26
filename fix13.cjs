const fs=require('fs');const p='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/RmiServerApplication.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('import org.springframework.boot.autoconfigure.SpringBootApplication;',
`import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;`);
c=c.replace('@SpringBootApplication',
`@SpringBootApplication(
        scanBasePackages = "com.unicconnect")
@EntityScan(basePackages = "com.unicconnect.entity")
@EnableJpaRepositories(basePackages = "com.unicconnect.repository")`);
fs.writeFileSync(p,c);console.log('scan widened');
