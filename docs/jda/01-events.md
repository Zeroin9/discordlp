# События и Слушатели (Events & Listeners)

### Start EventChannel on Ready Event

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Recommended way to start your EventChannel by passing the JDA instance from the ReadyEvent. Ensures JDA is fully initialized before your code runs.

```java
public static void main(String[] args) {
    JDABuilder.createDefault(TOKEN)
        .addEventListeners(listener) // some other listeners/settings
        .addEventListeners(new ListenerAdapter() {
            @Override public void onReady(ReadyEvent event) {
                new EventChannel(event.getJDA()).start(); // starts your channel with the ready event
            }
        }).build();
}
```

--------------------------------

### Basic Bot Setup with Ready Event Listener

Source: https://github.com/discord-jda/jda/wiki/Home

Demonstrates how to create a JDA instance and register a listener for the ReadyEvent. Requires a bot token as a command-line argument.

```java
public class ReadyListener implements EventListener
{
    public static void main(String[] args)
    throws LoginException
    {
        JDA jda = JDABuilder.createDefault(args[0])
            .addEventListeners(new ReadyListener()).build();
    }

    @Override
    public void onEvent(GenericEvent event)
    {
        if(event instanceof ReadyEvent)
            System.out.println("API is ready!");
    }
}
```

--------------------------------

### Bot Setup with Message Listener

Source: https://github.com/discord-jda/jda/wiki/Home

Shows how to set up a JDA bot and add a listener that handles incoming messages in both text channels and private messages. Requires a bot token.

```java
public class MessageListener extends ListenerAdapter
{
    public static void main(String[] args)
    throws LoginException
    {
        JDA jda = JDABuilder.createDefault(args[0]).build();
        jda.addEventListeners(new MessageListener());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.isFromType(ChannelType.TEXT))
        {
            System.out.printf("[%s][%s] %#s: %s%n", event.getGuild().getName(),
                event.getChannel().getName(), event.getAuthor(), event.getMessage().getContentDisplay());
        }
        else
        {
            System.out.printf("[PM] %#s: %s%n", event.getAuthor(), event.getMessage().getContentDisplay());
        }
    }
}
```

--------------------------------

### Java ListenerAdapter Example

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Use ListenerAdapter for event handling in JDA. Ensure you extend this class for proper event listener registration.

```java
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MyListener extends ListenerAdapter {
  ...
}
```

--------------------------------

### Implement a Message Receive Listener

Source: https://github.com/discord-jda/jda/blob/master/README.md

An example event listener that logs received messages to the console, displaying the channel, author, and message content.

```java
public class MessageReceiveListener extends ListenerAdapter {
  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    System.out.printf("[%s] %#s: %s\n",
      event.getChannel(),
      event.getAuthor(),
      event.getMessage().getContentDisplay());
  }
}
```

--------------------------------

### ListenerAdapter Implementation

Source: https://github.com/discord-jda/jda/wiki/1)-Events

Example of a listener extending ListenerAdapter for convenience. This allows overriding specific event methods like onMessageReceived.

```java
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class Test extends ListenerAdapter
{
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        System.out.println(event.getMessage().getContentDisplay());
    }
}
```

--------------------------------

### EventListener Implementation

Source: https://github.com/discord-jda/jda/wiki/1)-Events

Example of a listener implementing the EventListener interface. It overrides the onEvent method to handle specific event types like MessageReceivedEvent.

```java
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;

public class Test implements EventListener
{
    @Override
    public void onEvent(GenericEvent event)
    {
        if(event instanceof MessageReceivedEvent)
            System.out.println(event.getMessage().getContentDisplay());
    }
}
```

--------------------------------

### Java EventListener Implementation Example

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Implement the EventListener interface for JDA event handling. Ensure correct import of net.dv8tion.jda.api.hooks.EventListener and override the onEvent method.

```java
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;

public class MyListener implements EventListener {
  @Override
  public void onEvent(GenericEvent event) {
    ...
  }
}
```

--------------------------------

### Handle JDA Instance in Constructor

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Example of how to store the JDA instance in a class constructor. This is useful for accessing JDA functionalities from different parts of your application.

```java
public class EventChannel {
    private final JDA api;

    public EventChannel(JDA api) {
        this.api = api;
    }

    public void start() { ... }
}
```

--------------------------------

### Register Listener with JDABuilder

Source: https://github.com/discord-jda/jda/wiki/1)-Events

Demonstrates how to register a listener using JDABuilder when creating the JDA instance. Ensure your listener class is correctly implemented.

```java
import javax.security.auth.login.LoginException;

// imports {...}
public class Launcher
{
    public static void main(String[] arguments)
    throws LoginException, InterruptedException
    {
        JDA api = JDABuilder.createDefault(arguments[0])
                      .addEventListeners(new PingPongBot())
                      .build().awaitReady();
    }
}
```

--------------------------------

### Register Listener with JDA Instance

Source: https://github.com/discord-jda/jda/wiki/1)-Events

Shows how to register a listener with an existing JDA instance after it has been built. This is useful for dynamically adding listeners.

```java
import net.dv8tion.jda.api.JDA;

// imports {...}
public class MyListeners
{
    public static void registerPingPongListener(JDA api)
    {
        api.addEventListeners(new PingPongBot());
    }
}
```