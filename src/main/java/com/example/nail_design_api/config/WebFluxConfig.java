package com.example.nail_design_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
        configurer.defaultCodecs().maxInMemorySize(32 * 1024 * 1024);
    }

    @Bean
    public DefaultDataBufferFactory dataBufferFactory() {
        return new DefaultDataBufferFactory(true, 32 * 1024 * 1024);
    }
}