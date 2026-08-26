const fs=require('fs');const p='unicconnect-api/src/main/java/com/unicconnect/security/SecurityConfig.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;\n','');
c=c.replace(/    @Bean\n    public PasswordEncoder passwordEncoder\(\) \{\n        return new BCryptPasswordEncoder\(\);\n    \}\n\n/,
          '    // PasswordEncoder provided by uniconnect-core (shared with the RMI JVM)\n\n');
fs.writeFileSync(p,c);
console.log('dup removed:', !c.includes('BCryptPasswordEncoder'));
