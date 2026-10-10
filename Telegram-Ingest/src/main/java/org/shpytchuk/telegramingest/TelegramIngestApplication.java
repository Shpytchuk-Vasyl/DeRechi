package org.shpytchuk.telegramingest;

import org.shpytchuk.telegramingest.telegram.TelegramLogin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;

@SpringBootApplication
@EnableScheduling
public class TelegramIngestApplication {

    public static void main(String[] args) {
        if (Arrays.asList(args).contains(TelegramLogin.LOGIN_ARG)) {
            TelegramLogin.run(args);
            return;
        }
        SpringApplication.run(TelegramIngestApplication.class, args);
    }

}
