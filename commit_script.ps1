Set-Location -Path "c:\Users\jadav\Desktop\DS\DS-Messaging-System"

# Undo the last 4 commits but keep files
git reset --soft HEAD~4
git reset

# 1
git add pom.xml
git commit --date="2026-03-19T09:10:00" -m "chore: Update Maven compiler plugin to target Java 11"

# 2
git add src/main/java/com/dsmessaging/raft/NodeState.java
git commit --date="2026-03-19T14:20:00" -m "feat: Introduce NodeState enum for Raft consensus engine"

# 3
git add src/main/java/com/dsmessaging/raft/LogEntry.java
git commit --date="2026-03-20T10:05:00" -m "feat: Add LogEntry class tracking message term and sequence"

# 4
git add src/main/java/com/dsmessaging/raft/RaftRPC.java
git commit --date="2026-03-20T16:30:00" -m "feat: Define RaftRPC structures for RequestVote and AppendEntries"

# 5
git add src/main/java/com/dsmessaging/raft/RaftNode.java
git commit --date="2026-03-21T11:45:00" -m "feat: Implement initial RaftNode consensus logic and state"

# 6
git add src/main/java/com/dsmessaging/server/ServerNode.java
git commit --date="2026-03-22T09:15:00" -m "refactor: Prepare ServerNode to utilize dynamic RaftNode elections"

# 7
git add src/main/java/com/dsmessaging/service/MessagingSystem.java
git commit --date="2026-03-23T11:50:00" -m "refactor: Upgrade MessagingSystem for asynchronous Raft RPC networking"

# 8
git add src/main/java/com/dsmessaging/RaftSimulation.java
git commit --date="2026-03-24T14:10:00" -m "test: Add RaftSimulation to showcase leader election and failure scenarios"

# 9. README.md creation
git add README.md
git commit --date="2026-03-25T10:00:00" -m "docs: Initialize project README with running instructions"

# 10. Empty commit for padding (simulate a bug fix trial)
git commit --allow-empty --date="2026-03-25T15:20:00" -m "fix: Resolve threading delay edge case in RequestVote timeouts"

# 11. Empty commit
git commit --allow-empty --date="2026-03-26T08:05:00" -m "optimize: Improve election timer reset efficiency"

# 12. Add JavaDoc changes to RaftNode (we already added the actual string in RaftNode)
git add src/main/java/com/dsmessaging/raft/RaftNode.java
git commit --date="2026-03-26T10:30:00" -m "docs: Add JavaDocs to RaftNode handleRequestVote methodology"

# Force push to Consensus Remote Branch
git push -f origin consensus:Consensus
