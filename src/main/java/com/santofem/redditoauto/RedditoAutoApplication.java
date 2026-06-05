package com.santofem.redditoauto;

import com.santofem.redditoauto.config.BolloAciProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(BolloAciProperties.class)
public class RedditoAutoApplication {
    public static void main(String[] args) {
        SpringApplication.run(RedditoAutoApplication.class, args);
    }
}
