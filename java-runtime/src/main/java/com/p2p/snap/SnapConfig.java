package com.p2p.snap;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class SnapConfig {

    /** Hari untuk idempotensi X-EXTERNAL-ID dihitung dalam zona Asia/Jakarta. */
    @Bean
    public Clock snapClock() {
        return Clock.system(ZoneId.of("Asia/Jakarta"));
    }

    @Bean
    public FilterRegistrationBean<SnapHeaderFilter> snapHeaderFilter(SnapRoutes routes, ExternalIdRegistry registry,
                                                                     Clock snapClock, ObjectMapper json) {
        FilterRegistrationBean<SnapHeaderFilter> reg =
                new FilterRegistrationBean<>(new SnapHeaderFilter(routes, registry, snapClock, json));
        reg.addUrlPatterns("/v1.0/*");
        return reg;
    }
}
