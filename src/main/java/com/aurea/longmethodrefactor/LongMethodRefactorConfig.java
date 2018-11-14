package com.aurea.longmethodrefactor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LongMethodRefactorConfig {

    @Bean
    CommandLineRunner longMethodRefactorRunner(LongMethodRefactor longMethodRefactor) {
        return args -> longMethodRefactor.refactorLongMethods();
    }
}
