package com.dsmessaging;

import com.dsmessaging.grpc.*;
import com.dsmessaging.sync.ClockSync;
import com.dsmessaging.sync.HybridLogicalClock;
import com.dsmessaging.sync.MessageBuffer;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessagingServiceImpl extends MessagingServiceGrpc.MessagingServiceImplBase {
    private static final Logger logger = LoggerFactory.getLogger(MessagingServiceImpl.class);

    private final HybridLogicalClock hlc;
    private final ClockSync clockSync;
    private final MessageBuffer messageBuffer;

    public MessagingServiceImpl(HybridLogicalClock hlc, ClockSync clockSync) {
        this.hlc = hlc;
        this.clockSync = clockSync;
        // 500ms wait period for reordering
        this.messageBuffer = new MessageBuffer(500, content -> {
            logger.info("PROCESSED MESSAGE: {}", content);
        });
    }

    @Override
    public void sendMessage(MessageRequest request, StreamObserver<MessageResponse> responseObserver) {
        HLCTimestamp ts = request.getTimestamp();
        
        // Update HLC on receive
        hlc.updateRemote(ts.getWallTime(), ts.getLogicalCounter());
        
        logger.info("Received message from {}: '{}' with HLC {}", 
                request.getSenderId(), request.getContent(), ts);

        // Add to buffer for reordering
        messageBuffer.addMessage(request.getContent(), new HybridLogicalClock(
                ts.getWallTime(), ts.getLogicalCounter(), ts.getNodeId()));

        responseObserver.onNext(MessageResponse.newBuilder().setSuccess(true).build());
        responseObserver.onCompleted();
    }

    @Override
    public void syncClock(SyncRequest request, StreamObserver<SyncResponse> responseObserver) {
        long t1 = System.currentTimeMillis(); // Receive time
        long t2 = System.currentTimeMillis(); // Transmit time (simulating instantaneous)
        
        responseObserver.onNext(SyncResponse.newBuilder()
                .setT0(request.getT0())
                .setT1(t1)
                .setT2(t2)
                .build());
        responseObserver.onCompleted();
    }
}
