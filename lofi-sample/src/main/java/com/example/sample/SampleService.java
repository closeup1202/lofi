package com.example.sample;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.springframework.stereotype.Service;

@Service
public class SampleService {

    @WithSpan(value = "SampleService.hello", kind = SpanKind.INTERNAL)
    public String hello() {
        return "hello from lofi sample";
    }

    @WithSpan(value = "SampleService.slow", kind = SpanKind.INTERNAL)
    public String slow() throws InterruptedException {
        Thread.sleep(300);
        return "slow response";
    }

    @WithSpan(value = "SampleService.compute", kind = SpanKind.INTERNAL)
    public int compute(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) sum += i;
        return sum;
    }
}
