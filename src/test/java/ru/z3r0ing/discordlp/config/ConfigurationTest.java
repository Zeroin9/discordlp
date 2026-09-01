package ru.z3r0ing.discordlp.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Конфигурация приложения: регистрация slash-команд, подписка слушателей и порядок запуска Flyway.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationTest {

    @Mock
    private JDA jda;

    @Test
    void registersEveryPublicCommand() {
        CommandListUpdateAction action = Mockito.mock(CommandListUpdateAction.class, Mockito.RETURNS_SELF);
        when(jda.updateCommands()).thenReturn(action);

        new SlashCommandRegistrar(jda).registerCommands();

        ArgumentCaptor<CommandData[]> captor = ArgumentCaptor.forClass(CommandData[].class);
        verify(action).addCommands(captor.capture());

        assertThat(captor.getValue()).extracting(CommandData::getName)
                .containsExactlyInAnyOrder("lp", "lpuser", "lpadd", "lpremove", "lpkick", "lpmute", "lp-pari");
    }

    @Test
    void subscribesEveryListener() {
        ListenerAdapter first = Mockito.mock(ListenerAdapter.class);
        ListenerAdapter second = Mockito.mock(ListenerAdapter.class);

        new DiscordBotInitializer(jda, List.of(first, second)).onApplicationReady();

        verify(jda).addEventListener(first);
        verify(jda).addEventListener(second);
    }

    @Test
    void entityManagerFactoryWaitsForFlyway() {
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        BeanDefinition definition = Mockito.mock(BeanDefinition.class);
        when(beanFactory.containsBeanDefinition("entityManagerFactory")).thenReturn(true);
        when(beanFactory.getBeanDefinition("entityManagerFactory")).thenReturn(definition);
        when(definition.getDependsOn()).thenReturn(null);

        new FlywayConfig().postProcessBeanFactory(beanFactory);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(definition).setDependsOn(captor.capture());
        assertThat(captor.getValue()).containsExactly("flyway");
    }

    @Test
    void existingDependenciesArePreserved() {
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        BeanDefinition definition = Mockito.mock(BeanDefinition.class);
        when(beanFactory.containsBeanDefinition("entityManagerFactory")).thenReturn(true);
        when(beanFactory.getBeanDefinition("entityManagerFactory")).thenReturn(definition);
        when(definition.getDependsOn()).thenReturn(new String[]{"dataSource"});

        new FlywayConfig().postProcessBeanFactory(beanFactory);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(definition).setDependsOn(captor.capture());
        assertThat(captor.getValue()).containsExactly("dataSource", "flyway");
    }

    @Test
    void flywayDependencyIsNotDuplicated() {
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        BeanDefinition definition = Mockito.mock(BeanDefinition.class);
        when(beanFactory.containsBeanDefinition("entityManagerFactory")).thenReturn(true);
        when(beanFactory.getBeanDefinition("entityManagerFactory")).thenReturn(definition);
        when(definition.getDependsOn()).thenReturn(new String[]{"flyway"});

        new FlywayConfig().postProcessBeanFactory(beanFactory);

        verify(definition, never()).setDependsOn(any(String[].class));
    }

    @Test
    void missingEntityManagerFactoryIsIgnored() {
        ConfigurableListableBeanFactory beanFactory = Mockito.mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.containsBeanDefinition("entityManagerFactory")).thenReturn(false);

        new FlywayConfig().postProcessBeanFactory(beanFactory);

        verify(beanFactory, never()).getBeanDefinition("entityManagerFactory");
    }
}
