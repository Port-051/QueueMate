package com.queuemate.user.riot;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RiotProperties.class)
public class RiotConfig {
}
