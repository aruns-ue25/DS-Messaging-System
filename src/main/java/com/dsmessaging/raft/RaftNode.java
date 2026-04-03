package com.dsmessaging.raft;

import com.dsmessaging.model.Message;
import com.dsmessaging.server.ServerNode;
import com.dsmessaging.service.MessagingSystem;

import java.util.*;
import java.util.concurrent.*;

public class RaftNode {
    private final String serverId;
    private MessagingSystem messagingSystem;
    private ServerNode serverNode;

    // Persistent state
    private int currentTerm = 0;
    private String votedFor = null;
    private List<LogEntry> log = new ArrayList<>();

    // Volatile state
    private int commitIndex = 0;
    private int lastApplied = 0;

    // Leader state
    private Map<String, Integer> nextIndex = new ConcurrentHashMap<>();
    private Map<String, Integer> matchIndex = new ConcurrentHashMap<>();

    private NodeState state = NodeState.FOLLOWER;

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> electionTimer;
    private ScheduledFuture<?> heartbeatTimer;

    private Random random = new Random();
    private int votesReceived = 0;

    public RaftNode(String serverId, ServerNode serverNode) {
        this.serverId = serverId;
        this.serverNode = serverNode;
        // dummy log entry so 1-based indexing
        log.add(new LogEntry(0, 0, null));
    }

    public void setMessagingSystem(MessagingSystem ms) {
        this.messagingSystem = ms;
    }

    public synchronized void start() {
        if (!executor.isShutdown()) {
            resetElectionTimer();
        }
    }

    public synchronized void stop() {
        if (electionTimer != null)
            electionTimer.cancel(true);
        if (heartbeatTimer != null)
            heartbeatTimer.cancel(true);
        state = NodeState.FOLLOWER;
    }

    private void resetElectionTimer() {
        if (electionTimer != null) {
            electionTimer.cancel(true);
        }
        int timeout = 150 + random.nextInt(151); // 150ms - 300ms
        electionTimer = executor.schedule(this::startElection, timeout, TimeUnit.MILLISECONDS);
    }

    private synchronized void startElection() {
        if (!serverNode.isActive())
            return;

        state = NodeState.CANDIDATE;
        currentTerm++;
        votedFor = serverId;
        votesReceived = 1;

        System.out.println(serverId + " starts election for term " + currentTerm);

        // Immediate promotion if we are the only active node (Last Survivor)
        long activeCount = messagingSystem.getServers().stream().filter(s -> s.isActive()).count();
        int threshold = (int) (activeCount / 2 + 1);
        if (votesReceived >= threshold) {
            becomeLeader();
            return;
        }

        int lastLogIndex = log.size() - 1;
        int lastLogTerm = log.get(lastLogIndex).getTerm();

        RaftRPC.RequestVote request = new RaftRPC.RequestVote(currentTerm, serverId, lastLogIndex, lastLogTerm);
        messagingSystem.broadcastRequestVote(serverId, request);

        resetElectionTimer();
    }

    /**
     * Handles incoming RequestVote RPCs from other candidates.
     * 
     * @param request The RequestVote arguments
     * @return The RequestVoteReply result
     */
    public synchronized RaftRPC.RequestVoteReply handleRequestVote(RaftRPC.RequestVote request) {
        if (!serverNode.isActive())
            return new RaftRPC.RequestVoteReply(currentTerm, false);

        if (request.term > currentTerm) {
            currentTerm = request.term;
            state = NodeState.FOLLOWER;
            votedFor = null;
        }

        int lastLogIndex = log.size() - 1;
        int lastLogTerm = log.get(lastLogIndex).getTerm();

        boolean logIsUpToDate = (request.lastLogTerm > lastLogTerm) ||
                (request.lastLogTerm == lastLogTerm && request.lastLogIndex >= lastLogIndex);

        if (request.term == currentTerm && (votedFor == null || votedFor.equals(request.candidateId))
                && logIsUpToDate) {
            votedFor = request.candidateId;
            resetElectionTimer();
            return new RaftRPC.RequestVoteReply(currentTerm, true);
        }

        return new RaftRPC.RequestVoteReply(currentTerm, false);
    }

    public synchronized void handleRequestVoteReply(String voterId, RaftRPC.RequestVoteReply reply) {
        if (!serverNode.isActive() || state != NodeState.CANDIDATE)
            return;

        if (reply.term > currentTerm) {
            currentTerm = reply.term;
            state = NodeState.FOLLOWER;
            votedFor = null;
            resetElectionTimer();
            return;
        }

        if (reply.voteGranted) {
            votesReceived++;
            long activeCount = messagingSystem.getServers().stream().filter(ServerNode::isActive).count();
            int threshold = (int) (activeCount / 2 + 1);
            if (votesReceived >= threshold) {
                becomeLeader();
            }
        }
    }

    private synchronized void becomeLeader() {
        state = NodeState.LEADER;
        System.out.println(">>> " + serverId + " becomes LEADER for term " + currentTerm + " <<<");
        if (electionTimer != null)
            electionTimer.cancel(true);

        for (ServerNode node : messagingSystem.getServers()) {
            nextIndex.put(node.getServerId(), log.size());
            matchIndex.put(node.getServerId(), 0);
        }

        sendHeartbeats();
        heartbeatTimer = executor.scheduleAtFixedRate(this::sendHeartbeats, 0, 50, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendHeartbeats() {
        if (!serverNode.isActive() || state != NodeState.LEADER)
            return;

        for (ServerNode node : messagingSystem.getServers()) {
            if (node.getServerId().equals(this.serverId))
                continue;

            int prevLogIndex = nextIndex.get(node.getServerId()) - 1;
            int prevLogTerm = log.get(prevLogIndex).getTerm();

            List<LogEntry> entries = new ArrayList<>(log.subList(prevLogIndex + 1, log.size()));

            RaftRPC.AppendEntries request = new RaftRPC.AppendEntries(currentTerm, serverId, prevLogIndex, prevLogTerm,
                    entries, commitIndex);
            messagingSystem.sendAppendEntries(node.getServerId(), serverId, request);
        }
    }

    public synchronized RaftRPC.AppendEntriesReply handleAppendEntries(RaftRPC.AppendEntries request) {
        if (!serverNode.isActive())
            return new RaftRPC.AppendEntriesReply(currentTerm, false);

        if (request.term > currentTerm) {
            currentTerm = request.term;
            state = NodeState.FOLLOWER;
            votedFor = null;
        }

        if (request.term < currentTerm) {
            return new RaftRPC.AppendEntriesReply(currentTerm, false);
        }

        resetElectionTimer();

        if (state == NodeState.CANDIDATE) {
            state = NodeState.FOLLOWER;
        }

        if (request.prevLogIndex >= log.size() || log.get(request.prevLogIndex).getTerm() != request.prevLogTerm) {
            return new RaftRPC.AppendEntriesReply(currentTerm, false);
        }

        // Apply new entries
        int logInsertIndex = request.prevLogIndex + 1;
        for (LogEntry entry : request.entries) {
            if (logInsertIndex < log.size()) {
                if (log.get(logInsertIndex).getTerm() != entry.getTerm()) {
                    log.subList(logInsertIndex, log.size()).clear();
                    log.add(entry);
                }
            } else {
                log.add(entry);
            }
            logInsertIndex++;
        }

        if (request.leaderCommit > commitIndex) {
            commitIndex = Math.min(request.leaderCommit, log.size() - 1);
            applyLog();
        }

        return new RaftRPC.AppendEntriesReply(currentTerm, true);
    }

    public synchronized void handleAppendEntriesReply(String followerId, RaftRPC.AppendEntriesReply reply) {
        if (!serverNode.isActive() || state != NodeState.LEADER)
            return;

        if (reply.term > currentTerm) {
            currentTerm = reply.term;
            state = NodeState.FOLLOWER;
            votedFor = null;
            if (heartbeatTimer != null)
                heartbeatTimer.cancel(true);
            resetElectionTimer();
            return;
        }

        if (reply.success) {
            matchIndex.put(followerId, log.size() - 1);
            nextIndex.put(followerId, log.size());
            updateCommitIndex();
        } else {
            nextIndex.put(followerId, Math.max(1, nextIndex.get(followerId) - 1));
        }
    }

    private void updateCommitIndex() {
        for (int n = log.size() - 1; n > commitIndex; n--) {
            if (log.get(n).getTerm() == currentTerm) {
                int agreeCount = 1;
                for (ServerNode node : messagingSystem.getServers()) {
                    if (!node.getServerId().equals(this.serverId) && matchIndex.get(node.getServerId()) >= n) {
                        agreeCount++;
                    }
                }
                long activeCount = messagingSystem.getServers().stream().filter(ServerNode::isActive).count();
                int threshold = (int) (activeCount / 2 + 1);
                if (agreeCount >= threshold) {
                    commitIndex = n;
                    applyLog();
                    break;
                }
            }
        }
    }

    private void applyLog() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            Message msg = log.get(lastApplied).getMessage();
            if (msg != null) {
                serverNode.getMessageService().sendMessage(msg);
                System.out.println(serverId + " committed message: " + msg.getMessageId() + " at index " + lastApplied);
            }
        }
    }

    public synchronized void clientRequest(Message message) {
        if (state == NodeState.LEADER) {
            log.add(new LogEntry(currentTerm, log.size(), message));
            sendHeartbeats();
        } else {
            System.out.println(serverId + " is not the leader. Redirecting ignored for simulation simplicity.");
            // In a real system, redirect to leader.
        }
    }

    public NodeState getState() {
        return state;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }
}
