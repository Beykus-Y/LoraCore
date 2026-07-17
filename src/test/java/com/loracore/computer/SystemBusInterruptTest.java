package com.loracore.computer;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class SystemBusInterruptTest {

    @Test
    public void concurrentInterruptRequestsPreserveEveryIrqBit() throws Exception {
        SystemBus bus = new SystemBus();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] requesters = new Thread[16];

        for (int irq = 0; irq < requesters.length; irq++) {
            int requestedIrq = irq;
            requesters[irq] = new Thread(() -> {
                try {
                    start.await();
                    bus.requestInterrupt(requestedIrq);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            requesters[irq].start();
        }

        start.countDown();
        for (Thread requester : requesters) {
            requester.join();
        }

        for (int irq = 0; irq < 16; irq++) {
            assertEquals(irq, bus.checkPendingInterrupts());
            bus.clearInterrupt(irq);
        }
        assertEquals(-1, bus.checkPendingInterrupts());
    }
}
