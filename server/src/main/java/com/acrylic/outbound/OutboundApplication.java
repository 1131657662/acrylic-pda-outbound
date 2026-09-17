package com.acrylic.outbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OutboundApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(OutboundApplication.class, args);
        if (context.getEnvironment().getProperty("outbound.bootstrap.enabled", Boolean.class, false)) context.close();
    }
}
