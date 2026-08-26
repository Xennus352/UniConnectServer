const fs=require('fs');
function edit(p,pairs){let c=fs.readFileSync(p,'utf8');for(const [f,t] of pairs){if(!c.includes(f)){console.log('MISS '+p);continue;}c=c.split(f).join(t);}fs.writeFileSync(p,c);}
const F='uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/';
edit(F+'RmiExporter.java',[
 [`        try {\n            registry = LocateRegistry.createRegistry(props.getPort());\n            log.info("[RMI] embedded registry created on port {}", props.getPort());\n        } catch (AlreadyBoundException ignored) {\n            registry = LocateRegistry.getRegistry(props.getPort());\n            log.info("[RMI] reusing existing registry on port {}", props.getPort());\n        } catch (RemoteException e) {`,
  `        try {\n            registry = LocateRegistry.createRegistry(props.getPort());\n            log.info("[RMI] embedded registry created on port {}", props.getPort());\n        } catch (RemoteException e) {`],
 [`    private void bind(String name, Remote stub) throws RemoteException {\n        try {\n            registry.rebind(name, stub);\n            log.info("[RMI] bound {} -> rmi://{}:{}/{}", name,\n                    props.getHostname(), props.getPort(), name);\n        } catch (AlreadyBoundException e) {\n            throw new RemoteException("Binding collision for " + name, e);\n        }\n    }`,
  `    private void bind(String name, Remote stub) throws RemoteException {\n        registry.rebind(name, stub);\n        log.info("[RMI] bound {} -> rmi://{}:{}/{}", name,\n                props.getHostname(), props.getPort(), name);\n    }`],
 ['import java.rmi.AlreadyBoundException;\n',''],
]);
edit(F+'facade/FacadeGuard.java',[
 ['import com.unicconnect.exception.DuplicateResourceException;',
  'import com.unicconnect.exception.BusinessRuleException;\nimport com.unicconnect.exception.DuplicateResourceException;'],
]);
console.log('fixed');
