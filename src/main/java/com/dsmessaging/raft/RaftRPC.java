package com.dsmessaging.raft;

import java.util.List;

public class RaftRPC {

    public static class RequestVote {
        public final int term;
        public final String candidateId;
        public final int lastLogIndex;
        public final int lastLogTerm;

        public RequestVote(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
            this.term = term;
            this.candidateId = candidateId;
            this.lastLogIndex = lastLogIndex;
            this.lastLogTerm = lastLogTerm;
        }
    }

    public static class RequestVoteReply {
        public final int term;
        public final boolean voteGranted;

        public RequestVoteReply(int term, boolean voteGranted) {
            this.term = term;
            this.voteGranted = voteGranted;
        }
    }

    public static class AppendEntries {
        public final int term;
        public final String leaderId;
        public final int prevLogIndex;
        public final int prevLogTerm;
        public final List<LogEntry> entries;
        public final int leaderCommit;

        public AppendEntries(int term, String leaderId, int prevLogIndex, int prevLogTerm, List<LogEntry> entries,
                int leaderCommit) {
            this.term = term;
            this.leaderId = leaderId;
            this.prevLogIndex = prevLogIndex;
            this.prevLogTerm = prevLogTerm;
            this.entries = entries;
            this.leaderCommit = leaderCommit;
        }
    }

    public static class AppendEntriesReply {
        public final int term;
        public final boolean success;

        public AppendEntriesReply(int term, boolean success) {
            this.term = term;
            this.success = success;
        }
    }
}
