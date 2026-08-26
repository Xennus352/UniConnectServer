const fs=require('fs');const p='uniconnect-core/src/main/java/com/unicconnect/service/port/TimetableEventPort.java';
let c=fs.readFileSync(p,'utf8');
c=c.replace('    String DRAG_ENDED             = "DRAG_ENDED";',
 '    String DRAG_ENDED             = "DRAG_ENDED";\n    String TEACHING_GROUP_CREATED = "TEACHING_GROUP_CREATED";\n    String TEACHING_GROUP_DELETED = "TEACHING_GROUP_DELETED";\n    String COURSE_REQUIREMENT_CREATED = "COURSE_REQUIREMENT_CREATED";\n    String COURSE_REQUIREMENT_UPDATED = "COURSE_REQUIREMENT_UPDATED";\n    String COURSE_REQUIREMENT_DELETED = "COURSE_REQUIREMENT_DELETED";');
c=c.replace('    void publish(UUID lobbyId, String eventType, Map<String, Object> payload);',
 '    void publish(UUID lobbyId, String eventType, Map<String, Object> payload);\n\n    /** Term-scoped broadcast (teaching-group lifecycle). */\n    void publishForTerm(UUID termId, String eventType, Map<String, Object> payload);');
c=c.replace('    void publishForGeneration(UUID generationId, String eventType, Map<String, Object> payload);',
 '    void publishForGeneration(UUID generationId, String eventType, Map<String, Object> payload);\n\n    /** Course-scoped broadcast (course meeting requirements). */\n    void publishForCourse(UUID courseId, String eventType, Map<String, Object> payload);');
fs.writeFileSync(p,c);console.log('port extended');
