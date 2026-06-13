package ru.z3r0ing.discordlp.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * Ручная конфигурация Flyway, необходимая из-за отсутствия встроенной
 * FlywayAutoConfiguration в используемой версии Spring Boot.
 * Реализует BeanFactoryPostProcessor для гарантии того, что миграции
 * будут выполнены ДО инициализации entityManagerFactory (Hibernate).
 */
@Configuration
public class FlywayConfig implements BeanFactoryPostProcessor {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true) // Позволяет Flyway взять под контроль существующую схему
                .load();
        
        // Выполняем миграцию немедленно при создании бина
        flyway.migrate();
        
        return flyway;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // Принудительно указываем, что entityManagerFactory зависит от бина flyway.
        // Это гарантирует правильный порядок инициализации: сначала Flyway создаст/обновит
        // таблицы, и только после этого Hibernate начнет их валидацию (ddl-auto: validate).
        if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
            BeanDefinition emfBean = beanFactory.getBeanDefinition("entityManagerFactory");
            String[] dependsOn = emfBean.getDependsOn();
            
            if (dependsOn == null) {
                emfBean.setDependsOn(new String[]{"flyway"});
            } else if (!Arrays.asList(dependsOn).contains("flyway")) {
                String[] newDependsOn = Arrays.copyOf(dependsOn, dependsOn.length + 1);
                newDependsOn[dependsOn.length] = "flyway";
                emfBean.setDependsOn(newDependsOn);
            }
        }
    }
}