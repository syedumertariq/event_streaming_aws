package com.eventstreaming.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * AWS-specific database configuration for MySQL RDS connections.
 * 
 * This configuration provides optimized connection pools for AWS deployment
 * with MySQL RDS, specifically tuned for cloud environments.
 */
@Configuration
@Profile("aws-simple")
public class AwsDatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AwsDatabaseConfig.class);
    
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    
    @Value("${spring.datasource.username}")
    private String username;
    
    @Value("${spring.datasource.password}")
    private String password;
    
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;
    
    /**
     * Primary DataSource configuration with HikariCP optimized for AWS RDS.
     * 
     * This DataSource is used by both Spring Data JPA and Pekko Persistence JDBC.
     */
    @Bean
    @Primary
    public DataSource awsDataSource() {
        logger.info("Configuring HikariCP DataSource for AWS MySQL RDS connection");
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        
        // Connection pool settings optimized for AWS RDS
        config.setMaximumPoolSize(15);  // Slightly lower for AWS
        config.setMinimumIdle(3);       // Conservative for cost optimization
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(300000);  // 5 minutes (shorter for AWS)
        config.setMaxLifetime(1200000); // 20 minutes (shorter for AWS)
        config.setLeakDetectionThreshold(60000);
        
        // Performance optimizations for AWS RDS
        config.setPoolName("AwsEventStreamingHikariCP");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        // AWS RDS specific optimizations
        config.addDataSourceProperty("useSSL", "true");
        config.addDataSourceProperty("requireSSL", "false");
        config.addDataSourceProperty("verifyServerCertificate", "false");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("characterEncoding", "UTF-8");
        config.addDataSourceProperty("autoReconnect", "true");
        config.addDataSourceProperty("failOverReadOnly", "false");
        config.addDataSourceProperty("maxReconnects", "3");
        
        // Connection validation for AWS RDS
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        HikariDataSource dataSource = new HikariDataSource(config);
        logger.info("AWS HikariCP DataSource configured successfully for RDS: {}", 
                   jdbcUrl.replaceAll("password=[^&]*", "password=***"));
        
        return dataSource;
    }
}