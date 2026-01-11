package com.eventstreaming.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for comprehensive application monitoring.
 * Sets up JVM metrics, system metrics, and custom application metrics.
 */
@Configuration
public class MonitoringConfig {

    private static final Logger logger = LoggerFactory.getLogger(MonitoringConfig.class);

    /**
     * Configures JVM and system metrics collection.
     */
    @Autowired
    public void configureMeterRegistry(MeterRegistry meterRegistry) {
        // JVM metrics
        new JvmMemoryMetrics().bindTo(meterRegistry);
        new JvmGcMetrics().bindTo(meterRegistry);
        new JvmThreadMetrics().bindTo(meterRegistry);
        
        // System metrics
        new ProcessorMetrics().bindTo(meterRegistry);
        new UptimeMetrics().bindTo(meterRegistry);
        
        logger.info("Configured JVM and system metrics for monitoring");
    }
}