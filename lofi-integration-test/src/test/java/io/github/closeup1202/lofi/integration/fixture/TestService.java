package io.github.closeup1202.lofi.integration.fixture;

import org.springframework.stereotype.Service;

@Service
public class TestService {

    public static volatile long slowDelayMs = 10;

    public String fastMethod() {
        return "fast";
    }

    public String slowMethod() throws InterruptedException {
        Thread.sleep(slowDelayMs);
        return "slow";
    }
}
