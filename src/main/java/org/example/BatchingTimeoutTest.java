package org.example;

import org.apache.pulsar.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class BatchingTimeoutTest {

    private final static Logger log = LoggerFactory.getLogger(BatchingTimeoutTest.class);

    public static void main(String[] args) throws Exception {

        int producerCnt = 50;
        List<CompletableFuture<Void>> futureList = new ArrayList<>(producerCnt);
        final PulsarClient client = PulsarClient.builder()
                .serviceUrl("pulsar://localhost:6650")
                .build();
        final Executor testExecutor = Executors.newFixedThreadPool(producerCnt);
        for (int i = 0; i < producerCnt; i++) {
            final int j = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                BatchingTimeoutTest batchingTimeoutTest = new BatchingTimeoutTest();
                batchingTimeoutTest.produce(client, "producer" + j);
            }, testExecutor);
            futureList.add(future);
            Thread.sleep(1000);
        }

        futureList.forEach(CompletableFuture::join);
        client.close();
        log.info("############################## DONE ##############################");
        System.exit(0);
    }

    public void produce(PulsarClient client, String producerName) {
        try {
            int numMessages = 20;
            Producer<byte[]> producer = client.newProducer()
                    .topic("persistent://public/default/test-batching-timeout")
                    .enableBatching(true)
                    .batchingMaxPublishDelay(1, TimeUnit.MILLISECONDS)
                    .sendTimeout(5, TimeUnit.SECONDS)
                    .blockIfQueueFull(true)
                    .producerName(producerName)
                    .create();

            List<CompletableFuture<MessageId>> futureList = new ArrayList<>(numMessages);
            for (int i = 0; i < numMessages; i++) {
                try {
                    futureList.add(producer.sendAsync(("Message " + i).getBytes()));
                    log.info("producer " + producerName + " Queued message " + i);
                    Thread.sleep(5000); // > sendTimeout to trigger the bug
                } catch (Exception e) {
                    log.error("Failed producer {} to send message {}: {}", producerName, i, e.getMessage());
                }
            }

            futureList.forEach(CompletableFuture::join);
            producer.close();
        } catch (Exception ex) {
            log.error("error: ", ex);
        }
    }

}
