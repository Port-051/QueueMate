package com.queuemate.party;

import com.queuemate.party.service.PartyLifecycleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PartyLifecycleProperties.class)
public class PartyConfig {
}
