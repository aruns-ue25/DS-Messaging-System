$cp = (Get-Content -Path "classpath.txt") + ";target/classes"

Write-Host "--- Phase 1: Cluster Startup ---"
$p1 = Start-Process java -ArgumentList "-cp", $cp, "com.dsmessaging.MessagingServer", "50051", "node-1", "node-2:localhost:50052,node-3:localhost:50053" -PassThru -NoNewWindow
$p2 = Start-Process java -ArgumentList "-cp", $cp, "com.dsmessaging.MessagingServer", "50052", "node-2", "node-1:localhost:50051,node-3:localhost:50053" -PassThru -NoNewWindow
$p3 = Start-Process java -ArgumentList "-cp", $cp, "com.dsmessaging.MessagingServer", "50053", "node-3", "node-1:localhost:50051,node-2:localhost:50052" -PassThru -NoNewWindow
Start-Sleep -s 5

Write-Host "--- Phase 2: Normal Quorum Writes ---"
java -cp $cp com.dsmessaging.MessagingClient localhost:50051,localhost:50052,localhost:50053 client-final-1
Start-Sleep -s 2

Write-Host "--- Phase 3: Idempotency Test ---"
java -cp $cp com.dsmessaging.IdempotencyTest
Start-Sleep -s 2

Write-Host "--- Phase 4: Coordinator Failover (Killing Node 1) ---"
Stop-Process -Id $p1.Id -Force
java -cp $cp com.dsmessaging.MessagingClient localhost:50051,localhost:50052,localhost:50053 client-final-failover
Start-Sleep -s 2

Write-Host "--- Phase 5: Node Recovery (Restarting Node 1) ---"
$p1 = Start-Process java -ArgumentList "-cp", $cp, "com.dsmessaging.MessagingServer", "50051", "node-1", "node-2:localhost:50052,node-3:localhost:50053" -PassThru -NoNewWindow
Start-Sleep -s 5
java -cp $cp com.dsmessaging.MessagingClient localhost:50051,localhost:50052,localhost:50053 client-final-recovery

Write-Host "--- Final: Stopping All Nodes ---"
Stop-Process -Id $p1.Id, $p2.Id, $p3.Id -Force -ErrorAction SilentlyContinue
Write-Host "Verification Complete."
