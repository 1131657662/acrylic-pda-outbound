package com.acrylic.outbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OutboundApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(OutboundApplication.class, args);
        var environment = context.getEnvironment();
        if (environment.getProperty("outbound.bootstrap.enabled", Boolean.class, false)
                || environment.getProperty("outbound.exit-after-startup", Boolean.class, false)) context.close();
    }
}
