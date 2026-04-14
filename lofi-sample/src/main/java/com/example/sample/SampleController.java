package com.example.sample;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    @GetMapping("/hello")
    public String hello() {
        return sampleService.hello();
    }

    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        return sampleService.slow();
    }

    @GetMapping("/compute")
    public int compute(@RequestParam(defaultValue = "1000") int n) {
        return sampleService.compute(n);
    }
}
