const fs=require('fs');const p='uniconnect-api/src/main/java/com/unicconnect/security/SecurityConfig.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace(`    @Bean\n    public PasswordEncoder passwordEncoder() {\n        return new BCryptPasswordEncoder();\n    }\n\n`,
  `    // PasswordEncoder now provided by uniconnect-core (shared with the RMI JVM)\n\n`);
c=c.replace('import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;\n','');
fs.writeFileSync(p,c);
console.log('securityconfig updated');
