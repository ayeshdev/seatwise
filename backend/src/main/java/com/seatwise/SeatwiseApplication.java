package com.seatwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Scanning (rather than listing) keeps new @ConfigurationProperties records
// in any module bound without touching this class.
@SpringBootApplication
@ConfigurationPropertiesScan
public class SeatwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeatwiseApplication.class, args);
    }
}
