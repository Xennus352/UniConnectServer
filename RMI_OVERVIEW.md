# RMI in UniConnectServer — Complete Documentation

## Architecture Overview

Two separate JVMs share **uniconnect-core** (entities, repositories, services) and the **same PostgreSQL database**:

```
┌─────────────────────────────────────────────────────────────────┐
│  Spring Boot API (port 8080)                                    │
│  • REST + JWT authentication                                     │
│  • Controllers → Operations interfaces → RMI Clients            │
│  • rmi.enabled=false (default) = in-process                     │
│  • rmi.enabled=true   = remote calls via JRMP                   │
└──────────────────────────┬──────────────────────────────────────┘
                           │ RMI/JRMP (port 1099)
┌──────────────────────────▼──────────────────────────────────────┐
│  Standalone RMI Server (uniconnect-rmi-server module)           │
│  • Embedded RMI registry (LocateRegistry.createRegistry)        │
│  • Three facades bound: UserService, AttendanceService,         │
│    TimetableService                                             │
│  • Thin wrappers over shared UserService, AttendanceService,    │
│    TimetableGenerationService                                   │
│  • CallerContextVerifier validates HMAC + nonce + freshness     │
└─────────────────────────────────────────────────────────────────┘
```

## Module Structure

```
UniConnectServer/
├── uniconnect-core/                    # Shared domain, repositories, services
│   └── src/main/java/com/unicconnect/
│       ├── rmi/
│       │   ├── contract/               # CallerContext, CallerContextCodec, exceptions
│       │   ├── remote/                 # Remote interfaces (UserRemote, AttendanceRemote, TimetableRemote)
│       │   └── dto/                    # RMI DTOs + RmiMappers
│       └── service/                    # Business logic (shared by both JVMs)
│
├── unicconnect-api/                    # Spring Boot REST API (port 8080)
│   └── src/main/java/com/unicconnect/
│       ├── ops/                        # Operations interfaces + Local/Remote implementations
│       ├── rmi/client/                 # RMI client stubs + config
│       └── security/                   # CallerContextFactory
│
└── uniconnect-rmi-server/              # Standalone RMI server JVM
    └── src/main/java/com/unicconnect/rmi/server/
        ├── RmiServerApplication.java   # Entry point (keeps JVM alive)
        ├── RmiExporter.java            # Starts registry, binds facades
        ├── RmiServerProperties.java    # @ConfigurationProperties (rmi.*)
        ├── RmiCurrentUserHolder.java   # ThreadLocal for async generation
        ├── CallerContextVerifier.java  # Validates HMAC, nonce, TTL
        ├── FacadeGuard.java            # Exception translation
        └── facade/                     # Remote implementations
            ├── UserRemoteFacade.java
            ├── AttendanceRemoteFacade.java
            └── TimetableRemoteFacade.java
```

## Three Remote Interfaces

Located in `uniconnect-core/src/main/java/com/unicconnect/rmi/remote/`

### 1. UserRemote (binding: "UserService")
```java
List<UserDto> listUsers(CallerContext ctx)
UserDto getUser(UUID userId, CallerContext ctx)
List<UserDto> usersByRole(String roleName, CallerContext ctx)
UserDto createUser(CreateUserDto request, CallerContext ctx)
UserDto updateUser(UUID userId, UpdateUserDto request, CallerContext ctx)
UserDto updateStatus(UUID userId, UpdateStatusDto request, CallerContext ctx)
UserDto updateRole(UUID userId, UUID roleId, CallerContext ctx)
void deleteUser(UUID targetUserId, CallerContext ctx)
void deleteUsers(List<UUID> userIds, CallerContext ctx)
```

### 2. AttendanceRemote (binding: "AttendanceService")
```java
DailyReportDto dailyReport(UUID sessionId, CallerContext ctx)
MonthlyReportDto monthlyAttendance(UUID studentId, UUID courseId, int year, int month, CallerContext ctx)
List<AttendanceRowDto> markAttendance(UUID sessionId, MarkAttendanceDto request, CallerContext ctx)
AttendanceRowDto updateAttendance(UUID attendanceId, UpdateAttendanceDto request, CallerContext ctx)
void deleteAttendance(UUID attendanceId, CallerContext ctx)
```

### 3. TimetableRemote (binding: "TimetableService")
```java
// Queries
List<TimetableEntryDto> publishedTimetable(UUID termId, CallerContext ctx)
List<TimetableEntryDto> querySchedules(UUID termId, UUID sectionId, UUID staffId, Integer dayOfWeek, CallerContext ctx)
TimetableEntryDto getSchedule(UUID scheduleId, CallerContext ctx)

// Async Generation
GenerationHandleDto createGeneration(UUID termId, CallerContext ctx)
GenerationHandleDto startGeneration(UUID termId, GenerationRequestDto request, CallerContext ctx)
GenerationHandleDto runGeneration(UUID generationId, GenerationRequestDto request, CallerContext ctx)
GenerationStatusDto getGenerationStatus(UUID generationId, CallerContext ctx)
List<GenerationStatusDto> listGenerations(UUID termId, CallerContext ctx)
```

All interfaces:
- Extend `java.rmi.Remote`
- Throw `java.rmi.RemoteException`
- Accept `CallerContext` as last parameter for authentication

## Authentication: CallerContext

**Record** in `uniconnect-core/src/main/java/com/unicconnect/rmi/contract/CallerContext.java`:
```java
public record CallerContext(
    UUID userId,
    long epochMillis,
    UUID nonce,
    byte[] signature
) implements Serializable
```

### Flow
1. **API tier** (after JWT validation): `CallerContextFactory.forCurrentUser()`
   - Gets current userId from SecurityContext
   - Generates timestamp + random nonce
   - Signs with HMAC-SHA256 using shared secret (`rmi.sharedSecret`)

2. **RMI Server**: `CallerContextVerifier.verify(context)`
   - Recomputes HMAC, compares with signature
   - Checks timestamp within 30 seconds
   - Checks nonce not replayed (in-memory set with TTL)
   - Returns verified `userId` (UUID)

3. **Server re-reads roles/positions from DB** — never trusts client-sent roles

## Server Facades

Located in `uniconnect-rmi-server/src/main/java/com/unicconnect/rmi/server/facade/`

### UserRemoteFacade
- Delegates to `UserService`
- Maps domain objects → `UserDto` via `RmiMappers`

### AttendanceRemoteFacade
- Delegates to `AttendanceService`, `AttendanceCalculationService`, `RollCallService`
- Explicit `requireLecturer(caller)` checks for reports (calculation service is guard-free)
- Mark/update/delete enforce "assigned LECTURER + published timetable + session not completed"

### TimetableRemoteFacade
- Delegates to `ClassScheduleService`, `TimetableGenerationService`
- **Async generation**: single-thread `ExecutorService` (daemon)
- `RmiCurrentUserHolder` (ThreadLocal) propagates caller identity to worker thread
- Generation runs existing core solver — no algorithm duplication

## Client Side (API Module)

Located in `unicconnect-api/src/main/java/com/unicconnect/rmi/client/`

### RmiStubCache<T>
- Caches `Naming.lookup("rmi://host:port/binding")` result
- Thread-safe lazy initialization
- Reconnects on `RemoteException` / `ConnectException`

### Three Typed Clients
```java
UserRmiClient        -> UserRemote
AttendanceRmiClient  -> AttendanceRemote
TimetableRmiClient   -> TimetableRemote
```

### RmiClientProperties
```yaml
rmi:
  enabled: false          # toggle (default false)
  host: localhost
  port: 1099
  userBinding: UserService
  attendanceBinding: AttendanceService
  timetableBinding: TimetableService
  sharedSecret: uni-dev-secret-change-me
```

## Routing Switch: RmiRoutingConfig

In `unicconnect-api/src/main/java/com/unicconnect/ops/RmiRoutingConfig.java`

Uses `@ConditionalOnProperty(name = "rmi.enabled")` to swap **entire operation implementations**:

| Operation | rmi.enabled=false (default) | rmi.enabled=true |
|-----------|-----------------------------|------------------|
| User CRUD | `UserRouting.Local` → UserService | `UserRouting.Remote` → UserRmiClient |
| Attendance | `AttendanceRouting.Local` → AttendanceService | `AttendanceRouting.Remote` → AttendanceRmiClient |
| Timetable Query | `TimetableQueryRouting.Local` → ClassScheduleService | `TimetableQueryRouting.Remote` → TimetableRmiClient |
| Timetable Generation | `TimetableGenerationRouting.Local` → TimetableGenerationService | `TimetableGenerationRouting.Remote` → TimetableRmiClient |

**Controllers unchanged** — they depend on operation interfaces (`UserOperations`, `AttendanceOperations`, etc.)

## Configuration Properties

### RMI Server (`uniconnect-rmi-server`)
```yaml
rmi:
  port: 1099
  hostname: localhost
  userBinding: UserService
  attendanceBinding: AttendanceService
  timetableBinding: TimetableService
  sharedSecret: uni-dev-secret-change-me   # MUST match API module
```

### API Module (`unicconnect-api`)
```yaml
rmi:
  enabled: false
  host: localhost
  port: 1099
  userBinding: UserService
  attendanceBinding: AttendanceService
  timetableBinding: TimetableService
  sharedSecret: uni-dev-secret-change-me   # MUST match server
```

## Running Both JVMs

### Development
```bash
# Terminal 1: API (port 8080)
./mvnw spring-boot:run -pl unicconnect-api

# Terminal 2: RMI Server (port 1099)
./mvnw spring-boot:run -pl uniconnect-rmi-server
```

### Production (JARs)
```bash
# Build all
./mvnw clean package -DskipTests

# Terminal 1: API
java -jar unicconnect-api/target/unicconnect-api-*.jar

# Terminal 2: RMI Server
java -jar uniconnect-rmi-server/target/uniconnect-rmi-server-*.jar
```

### Enable RMI Mode
```yaml
# application.yml or application-prod.yml in unicconnect-api
rmi:
  enabled: true
```

## Key Design Decisions

1. **No separate `rmiregistry` process** — embedded registry in RMI server JVM (`LocateRegistry.createRegistry`)

2. **Shared secret HMAC** (not TLS) — internal network, simpler ops; secret must be identical in both modules

3. **Async generation** — runs on bounded single-thread executor inside RMI server; clients poll status via `getGenerationStatus`

4. **Facade pattern** — RMI layer is thin; all business logic stays in shared services (`uniconnect-core`)

5. **Zero REST contract changes** — controllers, DTOs, response formats identical; only transport changes

6. **ThreadLocal for async** — `RmiCurrentUserHolder` propagates verified caller to generation worker thread

7. **Nonce replay protection** — in-memory `ConcurrentHashMap<UUID, Long>` with 5-minute TTL cleanup

8. **Strong stub references** — `RmiExporter.exportedStubs` array prevents DGC from unexporting facades

## Exception Handling

- Server: `FacadeGuard.translate(RuntimeException)` → wraps as `RemoteException` with cause
- Client: `RemoteCall.execute()` catches `RemoteException`, unwraps cause, rethrows as Spring exception
- Preserves existing `@ExceptionHandler` mappings in REST layer

## File Reference Quick Links

| File | Purpose |
|------|---------|
| `addRmi.md` | Original requirements document |
| `uniconnect-rmi-server/.../RmiServerApplication.java` | Server entry point |
| `uniconnect-rmi-server/.../RmiExporter.java` | Registry startup + binding |
| `uniconnect-rmi-server/.../RmiServerProperties.java` | Server config |
| `uniconnect-rmi-server/.../facade/*.java` | Remote implementations |
| `uniconnect-core/.../remote/*.java` | Remote interfaces |
| `uniconnect-core/.../contract/CallerContext.java` | Auth context |
| `uniconnect-core/.../contract/CallerContextCodec.java` | HMAC sign/verify |
| `uniconnect-rmi-server/.../CallerContextVerifier.java` | Server-side verification |
| `unicconnect-api/.../RmiRoutingConfig.java` | Bean switch (local vs remote) |
| `unicconnect-api/.../rmi/client/RmiClientConfig.java` | Client bean definitions |
| `unicconnect-api/.../rmi/client/RmiStubCache.java` | Stub caching + reconnect |
| `unicconnect-api/.../security/CallerContextFactory.java` | Context builder |
| `unicconnect-api/.../ops/*Routing.java` | Local/Remote operation impls |

## Troubleshooting

| Symptom | Likely Cause |
|---------|--------------|
| `Connection refused` on port 1099 | RMI server not started |
| `RemoteException: AccessException` | `sharedSecret` mismatch between modules |
| `CallerContext rejected: timestamp` | System clocks >30s apart |
| `CallerContext rejected: nonce` | Replay attack detection (or clock skew) |
| Generation never completes | Check `generationExecutor` thread; see logs in RMI server |
| `ClassNotFoundException` on stub | Version mismatch; rebuild both modules |

## Security Notes

- RMI port (1099) should **not** be exposed externally — firewall / security group
- `sharedSecret` must be strong in production (rotate periodically)
- No TLS on JRMP — rely on network isolation
- All authorization (roles, positions) re-checked in services using DB data