package org.example;

import java.util.concurrent.CountDownLatch;

public class RenderApp {
    public static void main(String[] args) throws Exception {
        EventWebServer.start();
        EventRSVPBot.startBot();

        System.out.println("EventRSVP stack is running.");
        new CountDownLatch(1).await();
    }
}
