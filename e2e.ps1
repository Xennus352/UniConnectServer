param()
$ErrorActionPreference='Continue'
$java='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot\bin\java.exe'
$base='http://localhost:8080'
$root='D:\UniConnect\UniConnectServer'
Set-Location $root

# ---- 1. RMI SERVER (direct Neon, no pooler) ----
$dbDirect='jdbc:postgresql://ep-empty-leaf-azyy2750.c-3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&connectTimeout=60&socketTimeout=900'
$cmd="/c set `"DB_URL=$dbDirect`"&& set DB_USERNAME=neondb_owner&& set DB_PASSWORD=npg_PMrCxFm5s2If&& set RMI_SHARED_SECRET=uni-dev-secret-change-me&& `"$java`" -jar `"$root\uniconnect-rmi-server\target\uniconnect-rmi-server-0.0.1-SNAPSHOT.jar`""
$p1=Start-Process cmd -ArgumentList $cmd -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root\rmi-server.log" -RedirectStandardError "$root\rmi-server.err.log"
$dl=(Get-Date).AddSeconds(90); $up=$false
do { Start-Sleep 4
  if((Get-Content "$root\rmi-server.log" -EA SilentlyContinue | Select-String 'bound TimetableService')){$up=$true}
} until($up -or (Get-Date)-gt $dl)
if(-not $up){ Write-Output 'RMI SERVER FAILED'; Get-Content "$root\rmi-server.log" -Tail 15; exit 1 }
Write-Output ('[OK] RMI SERVER pid='+$p1.Id)

# ---- helper ----
function Login($email,$pw){
  Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body ('{"email":"'+$email+'","password":"'+$pw+'"}')
}

# ---- 2. HOD login + generation ----
$hod=Login 'dawmya@gmail.com' 'ucstgo@2026'
$h='Authorization: Bearer '+$hod.accessToken
$terms=Invoke-RestMethod "$base/api/terms" -Headers @{Authorization=$h}
$term=($terms|Where-Object{$_.status -eq 'ACTIVE'}|Select-Object -First 1); if(-not $term){$term=$terms[0]}
$tid=$term.termId
$code=curl.exe -s -o "$root\scope.json" -w '%{http_code}' -H $h "$base/api/generations/scope?termId=$tid&examTypeId=6a3c3800-f6e8-4611-adb3-587826cabf84"
node -e "const fs=require('fs');const sc=JSON.parse(fs.readFileSync('scope.json','utf8'));
fs.writeFileSync('gc.json',JSON.stringify({termId:sc[0] ? JSON.parse(fs.readFileSync('terms.json','utf8'))[0].termId : null}));
fs.writeFileSync('gr.json',JSON.stringify({examTypeId:'6a3c3800-f6e8-4611-adb3-587826cabf84',semesters:sc.map(s=>({semesterId:s.semesterId,sectionIds:s.sections.map(y=>y.sectionId)})),autoBindCurriculum:true}));"
$code=curl.exe -s -o g.json -w '%{http_code}' -H $h -H 'Content-Type: application/json' -X POST --data-binary "@gc.json" "$base/api/generations"
$gid=(Get-Content g.json -Raw|ConvertFrom-Json).generationId
Write-Output ('[OK] GENERATION CREATED gid='+$gid)
$t0=Get-Date
curl.exe -s -o r.json -H $h -H 'Content-Type: application/json' -X POST --data-binary "@gr.json" "$base/api/generations/$gid/generate"
Write-Output ('[OK] ASYNC SUBMIT ms='+[math]::Round(((Get-Date)-$t0).TotalMilliseconds))
$st=''
for($i=0;$i -lt 55;$i++){
  Start-Sleep 10
  try{ $j=Invoke-RestMethod "$base/api/generations/$gid" -Headers @{Authorization=$h} } catch { continue }
  if($j.status -ne $st){ Write-Output ('  status='+$j.status); $st=$j.status }
  if($j.status -in @('COMPLETED','FAILED','PUBLISHED')){
     if($j.failureReport){ ($j.failureReport -split "`n"|Select-Object -First 3) }
     break }
}
Write-Output ('GEN FINAL='+$st+' gid='+$gid)
Set-Content "$root\gen-result.txt" ("gid="+$gid+"; status="+$st) -Encoding ascii
if($st -ne 'COMPLETED'){ exit 2 }

# ---- 3. PUBLISH ----
$pub=Invoke-RestMethod "$base/api/generations/$gid/publish" -Method Post -Headers @{Authorization=$h}
Write-Output ('[OK] PUBLISHED at='+$pub.publishedAt)

# ---- 4. GET PUBLISHED TIMETABLE (lecturer) ----
$lec=Login 'dawmya@gmail.com' 'ucstgo@2026'
$hl='Authorization: Bearer '+$lec.accessToken
curl.exe -s -H $hl "$base/api/schedules/published?termId=$tid" -o pub.json
$pubSched=(Get-Content pub.json -Raw|ConvertFrom-Json)
Write-Output ('[OK] PUBLISHED SCHEDULES count='+$pubSched.Count)
# span census
$census=@{}
foreach($s in $pubSched){ $k="P"+$s.startPeriodNo+"-P"+$s.endPeriodNo; $census[$k]=1+([int]$census[$k]) }
$census.GetEnumerator()|Sort-Object Name|ForEach-Object{ Write-Output ('   span '+$_.Key+' x'+$_.Value) }
$p34=($pubSched|Where-Object{$_.startPeriodNo -eq 3 -and $_.endPeriodNo -eq 4}).Count
Write-Output ('P3-P4(lunch-crossing) rows='+$p34)

# ---- 5. ROLLCALL flow ----
curl.exe -s -H $hl "$base/api/rollcall/my-schedule" -o mysch.json
$mys=Get-Content mysch.json -Raw|ConvertFrom-Json
$sch=$mys|Where-Object{$_.dayName -eq 'Monday'}|Select-Object -First 1
$nextMon=(Get-Date).AddDays((([int][DayOfWeek]::Monday)-[int](Get-Date).DayOfWeek+7)%7).ToString('yyyy-MM-dd'); if($nextMon -le (Get-Date).ToString('yyyy-MM-dd')){$nextMon=(Get-Date).AddDays(7).ToString('yyyy-MM-dd')}
'{"scheduleId":"'+$sch.scheduleId+'","sessionDate":"'+$nextMon+'"}'|Out-File sess-req.json -Encoding ascii
$sess=Invoke-RestMethod "$base/api/rollcall/sessions" -Method Post -Headers @{Authorization=$hl} -ContentType 'application/json' -Body (Get-Content sess-req.json -Raw)
$sid=$sess.sessionId
Write-Output ('[OK] SESSION sid='+$sid+' date='+$sess.sessionDate+' course='+$sess.courseCode)
curl.exe -s -H $hl "$base/api/rollcall/students?scheduleId=$($sch.scheduleId)&sessionId=$sid" -o stu.json
$stu=Get-Content stu.json -Raw|ConvertFrom-Json
$a=$stu.students[0]; $b=$stu.students[1]
$s0=$stu.slots[0].slotId; $sl=$stu.slots[$stu.slots.Count-1].slotId
$body='{entries:['+
 '{studentId:"'+$a.studentId+'",attendanceStatus:"PRESENT",remark:"rmi-e2e",attendanceStartSlotId:"'+$s0+'",attendanceEndSlotId:"'+$sl+'"},'+
 '{studentId:"'+$b.studentId+'",attendanceStatus:"ABSENT"}]}'
$body=$body.Replace('"','\"')
node -e "const fs=require('fs');const s=JSON.parse(fs.readFileSync('stu.json','utf8'));const a=s.students[0],b=s.students[1];const sl0=s.slots[0].slotId,sln=s.slots[s.slots.length-1].slotId;
fs.writeFileSync('mark.json',JSON.stringify({entries:[{studentId:a.studentId,attendanceStatus:'PRESENT',remark:'rmi-e2e',attendanceStartSlotId:sl0,attendanceEndSlotId:sln},{studentId:b.studentId,attendanceStatus:'ABSENT'}]}));"
$marked=Invoke-RestMethod "$base/api/attendance/$sid/mark" -Method Post -Headers @{Authorization=$hl} -ContentType 'application/json' -Body (Get-Content mark.json -Raw)
Write-Output ('[OK] MARK rows='+$marked.Count+' statuses='+(($marked|ForEach-Object{$_.attendanceStatus}) -join ','))
# reload
curl.exe -s -H $hl "$base/api/rollcall/students?scheduleId=$($sch.scheduleId)&sessionId=$sid" -o stu2.json
$stu2=Get-Content stu2.json -Raw|ConvertFrom-Json
$persisted=($stu2.students|Where-Object{$_.attendanceStatus})
Write-Output ('[OK] RELOAD persisted='+$persisted.Count+' -> '+(($persisted|ForEach-Object{$_.rollNo+':'+$_.attendanceStatus}) -join ' '))
$daily=Invoke-RestMethod "$base/api/rollcall/report/daily/$sid" -Headers @{Authorization=$hl}
Write-Output ('[OK] DAILY course='+$daily.courseCode+' scheduled='+$daily.scheduledPeriods+' present='+($daily.students|Where-Object{$_.status -eq 'PRESENT'}).Count)
Set-Content "$root\e2e-summary.txt" @"
gid=$gid
session=$sid
schedule=$($sch.scheduleId)
course=$($sch.courseCode)
markedRows=$($marked.Count)
publishedCount=$($pubSched.Count)
"@ -Encoding ascii
Write-Output 'E2E DONE'
