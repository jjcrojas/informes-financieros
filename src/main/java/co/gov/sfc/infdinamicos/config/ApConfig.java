package co.gov.sfc.infdinamicos.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;
import org.springframework.beans.factory.annotation.Value;

import java.util.Locale;

import javax.sql.DataSource;

@Configuration
public class ApConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;


    @Bean
    public LocaleResolver localeResolver() {
        // Configura el locale a español de Colombia
        return new FixedLocaleResolver(new Locale("es", "CO"));
    }

    
    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMinimumIdle(5);
        dataSource.setMaximumPoolSize(15);
        dataSource.setIdleTimeout(30000);
        dataSource.setMaxLifetime(2000000);
        dataSource.setConnectionTimeout(30000);
        return dataSource;
    }
}