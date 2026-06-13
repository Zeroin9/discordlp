# Конфигурация и Кэширование (Configuration & Caching)

### Create a Basic Message Logging Bot

Source: https://github.com/discord-jda/jda/blob/master/README.md

This snippet demonstrates how to initialize a JDA bot with light caching and specific intents for message handling. It attaches a listener to process incoming messages.

```java
public static void main(String[] args) {
  JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
      .addEventListeners(new MessageReceiveListener())
      .build();
}
```

--------------------------------

### Enable Default Intents and Add Privileged Intent

Source: https://github.com/discord-jda/jda/wiki/Gateway-Intents-and-Member-Cache-Policy

Use createDefault to enable all default intents and then use enableIntents to add a privileged intent like GUILD_MEMBERS. This allows for broader event handling while still requiring specific configuration for sensitive data.

```java
JDABuilder.createDefault(token) // enable all default intents
          .enableIntents(GatewayIntent.GUILD_MEMBERS) // also enable privileged intent
          .addEventListeners(new JoinListener())
          .addEventListeners(new CommandHandler())
          .build();
```

--------------------------------

### Create JDA with Light Configuration and Specific Intents

Source: https://github.com/discord-jda/jda/wiki/Gateway-Intents-and-Member-Cache-Policy

Use createLight to disable unused cache flags and enable only the necessary intents like GUILD_MESSAGES and GUILD_MEMBERS for basic bot functionality. This configuration does not cache members.

```java
public static void main(String[] args) {
  // createLight disables unused cache flags
  // GUILD_MESSAGES enables events for messages sent in guilds
  // GUILD_MEMBERS gives you access to guild member join events so you can send welcome messages
  // The resulting JDA instance will not cache any members since createLight disables it.
  JDABuilder.createLight(BOT_TOKEN, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
            .addEventListeners(new JoinListener())
            .addEventListeners(new CommandHandler())
            .build();
}
```

--------------------------------

### Register Event Listener with JDA

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Demonstrates how to register an event listener with the JDA instance. Ensure your listener class extends ListenerAdapter or implements EventListener and that the correct GatewayIntents are enabled.

```java
jda.addEventListener(new MyListener())
```

--------------------------------

### Generate Thread Dump with jstack

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Use the jstack utility with the -l flag to generate a thread dump of a running JVM process and redirect output to a file.

```bash
jstack -l <pid>
```

```bash
jstack -l 1337 > dump.txt
```

--------------------------------

### Enable Lazy Loading with JDABuilder

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Use JDABuilder#setChunkingFilter(ChunkingFilter.NONE) to enable lazy loading, only loading members who actively participate in servers.

```java
JDABuilder#setChunkingFilter(ChunkingFilter.NONE)
```

--------------------------------

### Annotated Event Listener

Source: https://github.com/discord-jda/jda/wiki/1)-Events

Demonstrates using the AnnotatedEventManager with methods annotated with @SubscribeEvent. Each method should accept a single event parameter.

```java
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.exceptions.LoginException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.managers.EventManager;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;

public class Test
{
    private static final String TOKEN = "YOUR_BOT_TOKEN"; // Replace with your actual token

    public static void main(String[] args)
    throws LoginException
    {
        JDABuilder.createDefault(TOKEN)
            .setEventManager(new AnnotatedEventManager())
            .addEventListeners(new Test())
            .enableIntents(GatewayIntent.MESSAGE_CONTENT) // Enable message content intent
            .build();
    }

    @SubscribeEvent
    public void ohHeyAMessage(MessageReceivedEvent event)
    {
        System.out.println(event.getMessage().getContentDisplay());
    }
}
```

--------------------------------

### Enable GUILD_MEMBERS Intent and Set Member Cache Policy to ALL

Source: https://github.com/discord-jda/jda/wiki/Gateway-Intents-and-Member-Cache-Policy

This snippet shows how to enable the GUILD_MEMBERS intent and set the MemberCachePolicy to ALL when building a JDA instance. This configuration ensures all members are cached once loaded.

```java
JDABuilder.createDefault(token)
          .enableIntents(GatewayIntent.GUILD_MEMBERS)
          .setMemberCachePolicy(MemberCachePolicy.ALL)
          .build();
```

--------------------------------

### Disable Member Cache with JDABuilder

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Configure the member cache policy using JDABuilder#setMemberCachePolicy() to control which members are cached, using NONE to cache no users.

```java
JDABuilder#setMemberCachePolicy(MemberCachePolicy)
```

--------------------------------

### Disable Gateway Intents with JDABuilder

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Disable unused gateway intents using JDABuilder#disabledIntents() to reduce the number of events received and lower memory usage.

```java
JDABuilder#disabledIntents(EnumSet)
```

--------------------------------

### Disable Cache Flags with JDABuilder

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Disable specific cache flags like presences or channel permission overrides using JDABuilder#disableCache() to reduce memory footprint.

```java
JDABuilder#disableCache(CacheFlag...)
```