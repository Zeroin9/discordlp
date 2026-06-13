# Асинхронные операции (RestAction)

### Start EventChannel by Awaiting Ready

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Alternative method to start your EventChannel by blocking until JDA is ready. Use this if you need the JDA instance immediately after building.

```java
public static void main(String[] args) {
    JDA api = JDABuilder.createDefault(TOKEN)
        .addEventListeners(listener) // some other listeners/settings
        .build();
    new EventChannel(api.awaitReady()).start();
}
```

--------------------------------

### Example submitAfter

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Demonstrates sending a message with a delay using submitAfter and retrieving the result.

```java
private Map<String, DelayedCompletableFuture<Message>> tasks = new HashMap<>();

public ScheduledFuture<Message> sendWithTask(MessageChannel channel, String message)
{
    DelayedCompletable<Message> task = channel.sendMessage(message).submitAfter(5, TimeUnit.SECONDS);
    return task;
}

public void doSomething(MessageChannel channel, String message)
throws Exception
{
    tasks.add(channel.getId(), sendWithTask(channel, message));
    for (DelayedCompletable<Message> task : tasks.values())
    {
        // non-blocking alternative is `thenAccept`
        System.out.printf("Task completed: %s\n", task.get());
    }
}
```

--------------------------------

### JavaDoc Linking Example

Source: https://github.com/discord-jda/jda/wiki/6)-JDA-Structure-Guide

Demonstrates correct JavaDoc linking using fully qualified names and external hyperlinks with target="_blank".

```Java
/**
 * <p>The following {@link net.dv8tion.jda.core.requests.ErrorResponse ErrorResponses} are possible:
 * <ul>
 *     <li>{@link net.dv8tion.jda.core.requests.ErrorResponse#MISSING_ACCESS MISSING_ACCESS}
 *     <br>The request was attempted after the account lost access to the
 *         {@link net.dv8tion.jda.core.entities.Guild Guild} or {@link net.dv8tion.jda.client.entities.Group Group}
 *         typically due to being kicked or removed, or after {@link net.dv8tion.jda.core.Permission#MESSAGE_READ Permission.MESSAGE_READ}
 *         was revoked in the {@link net.dv8tion.jda.core.entities.TextChannel TextChannel}</li>
 *
 *     <li>{@link net.dv8tion.jda.core.requests.ErrorResponse#MISSING_PERMISSIONS MISSING_PERMISSIONS}
 *     <br>The send request was attempted after the account lost {@link net.dv8tion.jda.core.Permission#MESSAGE_WRITE Permission.MESSAGE_WRITE} in
 *         the {@link net.dv8tion.jda.core.entities.TextChannel TextChannel}.</li>
 * </ul>
 */
```

```Java
/**
 * @return {@link net.dv8tion.jda.core.requests.RestAction RestAction} - Type: String
 *         <br>This is an -optional- description of what this RestAction will provide in case the type isn't enough
 */
```

--------------------------------

### Example Mixin with Usage

Source: https://github.com/discord-jda/jda/blob/master/src/main/java/net/dv8tion/jda/internal/entities/channel/mixin/README.md

Demonstrates a public entity API interface, an internal mixin interface for code reuse and state exposure, and a concrete internal implementation.

```java
public interface SomeEntity {
    String getName();
    
    default String getNameTwice() {
      return getName() + "-" + getName();
    }

    RestAction<Void> updateName(String name);
}

public interface SomeEntityMixin<T extends SomeEntityMixin<T>> extends SomeEntity {
    //---- Default implementations of interface ----
    @Override
    default RestAction<Void> updateName(String name) {
        checkCanModifyEntity();
        
        Route.CompiledRoute route = Route.custom(Method.POST, "/someEntity/name/");
        return new RestActionImpl<>(route);
    }
    
    //---- State Accessors ----
    T setName(String name);
    
    //---- Mixin Hooks -----
    void checkCanModifyEntity();
}

public class SomeEntityImpl implements SomeEntityMixin<SomeEntityImpl> {
    private String name;
    
    public SomeEntityImpl() {}
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public SomeEntityImpl setName(String name) {
        this.name = name;
    }
    
    @Override
    public void checkCanModifyEntity() {
        //Do some check here, throw if check is bad!
    }
}
```

--------------------------------

### Executing a Complex RestAction

Source: https://github.com/discord-jda/jda/blob/master/README.md

Shows how to execute a previously defined complex RestAction, such as the selfDestruct example, by calling its queue() method.

```java
selfDestruct(channel, "Hello friend, this is my secret message").queue();
```

--------------------------------

### Queue After Delay Example

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Opens a private channel to a user and sends a reminder message after a specified delay. This operation is non-blocking.

```java
public void remind(User user, String reminder, long delay, TimeUnit unit)
{
    user.openPrivateChannel().queue(
        (channel) -> channel.sendMessage(reminder).queueAfter(delay, unit)
    );
}

public void remindAlternate(User user, String reminder, long delay, TimeUnit unit)
{
    user.openPrivateChannel().queueAfter(delay, unit,
        (channel) -> channel.sendMessage(reminder).queue()
    );
}
```

--------------------------------

### Implement Message Listener for Ping-Pong

Source: https://github.com/discord-jda/jda/wiki/3)-Getting-Started

Create a custom listener that extends ListenerAdapter to handle incoming messages. This example responds with 'Pong!' when it receives the '!ping' command. It filters out messages from bots and ensures the response is sent to the correct channel.

```java
public class MyListener extends ListenerAdapter 
{
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.getAuthor().isBot()) return;
        // We don't want to respond to other bot accounts, including ourself
        Message message = event.getMessage();
        String content = message.getContentRaw(); 
        // getContentRaw() is an atomic getter
        // getContentDisplay() is a lazy getter which modifies the content for e.g. console view (strip discord formatting)
        if (content.equals("!ping"))
        {
            MessageChannel channel = event.getChannel();
            channel.sendMessage("Pong!").queue(); // Important to call .queue() on the RestAction returned by sendMessage(...)
        }
    }
```

--------------------------------

### Push Commits to Origin

Source: https://github.com/discord-jda/jda/wiki/5)-Contributing

Push your local commits to your remote fork's branch. This example pushes the 'patch-1' branch.

```sh
$ git push origin patch-1
Counting objects: 3, done.
Delta compression using up to 8 threads.
Compressing objects: 100% (3/3), done.
Writing objects: 100% (3/3), 313 bytes | 0 bytes/s, done.
Total 3 (delta 2), reused 0 (delta 0)
remote: Resolving deltas: 100% (2/2), completed with 2 local objects.
To https://github.com/ExampleName/JDA.git
 * [new branch]      patch-1 -> patch-1
```

--------------------------------

### Complete After Delay Example

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Edits a message and waits for the operation to complete after a specified delay. This method blocks the current thread.

```java
public Message waitForEdit(Message message)
{
    return message.editMessage("5 Minutes are over").completeAfter(5, TimeUnit.MINUTES);
}
```

--------------------------------

### Get or Create Permission Override Blocking

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Retrieves a permission override for a member on a channel, or creates one if it doesn't exist. This operation blocks until completion.

```java
public PermissionOverride getOverride(Channel channel, Member member)
{
    final PermissionOverride override = channel.getPermissionOverride(member);
    
    if (override == null)
        return channel.createPermissionOverride(member).complete();
    
    return override;
}
```

--------------------------------

### Get or Create Permission Override Asynchronously

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Retrieves a permission override for a member on a channel, or creates one if it doesn't exist. This operation returns a CompletableFuture for asynchronous handling.

```java
public CompletableFuture<PermissionOverride> getOverride(Channel channel, Member member)
{
    final PermissionOverride override = channel.getPermissionOverride(member);
    
    if (override == null)
        return channel.createPermissionOverride(member).submit();
    
    return CompletableFuture.completedFuture(override);
}

getOverride(channel, member).thenAccept(override -> ...);
```

--------------------------------

### Basic RestAction Usage

Source: https://github.com/discord-jda/jda/blob/master/README.md

Demonstrates chaining builder methods to configure an API request and sending it asynchronously using queue().

```java
channel.sendMessage("Hello Friend!")
  .addFiles(FileUpload.fromData(greetImage)) // Chain builder methods to configure the request
  .queue() // Send the request asynchronously
```

--------------------------------

### Copy and Rename a Channel

Source: https://github.com/discord-jda/jda/wiki/12)-Separation-of-Concerns

Shows how to create a copy of an existing channel and set a new name for the copy before executing the creation RestAction. The `queue()` method executes the creation asynchronously.

```java
public void copyChannel(Channel channel, String newName) {
    channel.createCopy().setName(newName).queue();
}
```

--------------------------------

### Update Text Channel Name and Topic

Source: https://github.com/discord-jda/jda/wiki/12)-Separation-of-Concerns

Demonstrates how to update both the name and topic of a TextChannel using its manager. The `queue()` method executes the updates asynchronously.

```java
public void updateChannel(TextChannel channel) {
    ChannelManager manager = channel.getManager(); // get the manager
    manager.setName("testing-2").setTopic("This is a testing channel, no memes allowed"); // set the new values
    manager.queue(); // execute update, this updates both name and topic
}
```

--------------------------------

### Configure Thread Pools with JDABuilder

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Adjust the threading configuration for JDA by setting the callback, gateway, and rate limit pools using JDABuilder setters.

```java
JDABuilder#setCallbackPool(ExecutorService)
```

```java
JDABuilder#setGatewayPool(ScheduledExecutorService)
```

```java
JDABuilder#setRateLimitPool(ScheduledExecutorService)
```

--------------------------------

### Send Private Message (Single-line Callback)

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

A concise version for sending a private message, using a single-line lambda expression for the callback to open and send a message.

```java
public void sendPrivateMessage(User user, String content)
{
    // notice that we are not placing a semicolon (;) in the callback this time!
    user.openPrivateChannel().queue( (channel) -> channel.sendMessage(content).queue() );
}
```

--------------------------------

### Send Private Message (Multi-line Callback)

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Opens a private channel to a user and sends a message within a callback. The callback accesses variables from the enclosing scope.

```java
public void sendPrivateMessage(User user, String content)
{
    // openPrivateChannel provides a RestAction<PrivateChannel> 
    // which means it supplies you with the resulting channel
    user.openPrivateChannel().queue((channel) ->
    {
        // value is a parameter for the `accept(T channel)` method of our callback. 
        // here we implement the body of that method, which will be called later by JDA automatically.
        channel.sendMessage(content).queue();
        // here we access the enclosing scope variable -content-
        // which was provided to sendPrivateMessage(User, String) as a parameter
    });
}
```

--------------------------------

### Send Message with Success Callback (Lambda Expression)

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Sends a message using a lambda expression for the success callback, providing a more concise way to handle the response and log the sent message.

```java
public void sendAndLog(MessageChannel channel, String message)
{
    // Here we use a lambda expressions which names the callback parameter -response- and uses that as a reference
    // in the callback body -System.out.printf("Sent Message %s\n", response)-
    Consumer<Message> callback = (response) -> System.out.printf("Sent Message %s\n", response);
    channel.sendMessage(message).queue(callback); // ^ calls that
}
```

--------------------------------

### Enable RestAction Context and Default Failure

Source: https://github.com/discord-jda/jda/wiki/10)-FAQ

Enables passing context to RestActions and sets a default failure handler to print stack traces. Useful for debugging RestAction errors, but may impact performance.

```java
RestAction.setPassContext(true); // enable context by default
RestAction.DEFAULT_FAILURE = Throwable::printStackTrace;
```

--------------------------------

### JavaDoc Escaping and Code Tag Usage

Source: https://github.com/discord-jda/jda/wiki/6)-JDA-Structure-Guide

Shows how to use JavaDoc tags for literal text, inline code, and HTML-like code snippets. It demonstrates '{@literal}', '{@code}', and '<code>' tags, highlighting when to close tags.

```java
/**
 * JDA {@literal >} All others
 * <br>Because we can do `{@code channel.sendMessage("hey").queue()}` AND `{@code channel.sendMessage("hey").complete()}`!
 * 
 * <p>Or even do <code>channel.sendMessage("hey").{@link net.dv8tion.jda.core.requests.RestAction#submit submit()}</code>!!
 */
```

--------------------------------

### Preventing Deadlock with Callbacks

Source: https://github.com/discord-jda/jda/wiki/19)-Troubleshooting

Illustrates a deadlock scenario when using `complete()` within a callback thread. It's recommended to use `queue()` instead for asynchronous operations.

```java
class Main {
    public static main(String[] args) {
        JDA api = JDABuilder.createDefault(BOT_TOKEN)
                .setCallbackPool(Executors.newSingleThreadScheduledExecutor()) // (1)
                .build().awaitReady();
        TextChannel channel = api.getTextChannelById(CHANNEL_ID);
        channel.sendMessage("hello there").queue((message) -> { // (2)
            System.out.println("Hello");
            message.editMessage("general kenobi").complete(); // (3) deadlock
            System.out.println("World!!!!"); // never printed
        });
   }
}
```

--------------------------------

### Send Message with Success Callback (Anonymous Class)

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Sends a message and uses an inline anonymous Consumer implementation to handle the response, logging the sent message.

```java
public void sendAndLog(MessageChannel channel, String message) 
{
    channel.sendMessage(message).queue(new Consumer<Message>()
    {
        @Override
        public void accept(Message t)
        {
            System.out.printf("Sent Message %s\n", t);
        }
    });
}
```

--------------------------------

### Send Message and Log Blocking

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Sends a message to a channel and completes the operation, then logs the sent message. This is a blocking operation.

```java
public Message sendAndLog(MessageChannel channel, String message)
{
    Message response = channel.sendMessage(message).complete();
    System.out.printf("Sent Message %s\n", response);
    return response;
}
```

--------------------------------

### Send Message with queue()

Source: https://github.com/discord-jda/jda/wiki/7)-Using-RestAction

Executes a RestAction asynchronously to send a message. The actual sending might occur after the method call returns.

```java
public void sendMessage(MessageChannel channel, String message) 
{
    channel.sendMessage(message).queue();
}
```