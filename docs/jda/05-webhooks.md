# Вебхуки (Webhooks)

### Get WebhookClient Example

Source: https://github.com/discord-jda/jda/wiki/9)-Webhooks

Demonstrates how to obtain a WebhookClient instance from a Webhook object. Remember to close the client when done.

```java
Webhook webhook; // some webhook instance
WebhookClientBuilder builder = webhook.newClient();
WebhookClient client = builder.build(); //remember to close this client when you are done
```

--------------------------------

### Build and Configure WebhookCluster

Source: https://github.com/discord-jda/jda/wiki/9)-Webhooks

Demonstrates creating a WebhookCluster, setting default options like daemon threads, and adding webhooks using their ID and token or existing Webhook objects.

```java
WebhookCluster cluster = new WebhookCluster();
cluster.setDefaultDaemon(true); // make all builders daemon
cluster.buildWebhook(webhookId, webhookToken); // automatically adds the built webhook
cluster.addWebhooks(webhook);
```

--------------------------------

### Broadcast and Multicast Messages with WebhookCluster

Source: https://github.com/discord-jda/jda/wiki/9)-Webhooks

Shows how to send messages to all webhooks in a cluster (broadcast) or to a filtered subset of webhooks (multicast) using the WebhookCluster.

```java
cluster.broadcast("PSA: JDA is pretty powerful");
WebhookMessage message = new WebhookMessageBuilder().setContent("This is only for you: I love you <3").build();
cluster.multicast((client) -> client.getIdLong() == 351016865780334613L, message);
```

--------------------------------

### Send Webhook Message with Embeds

Source: https://github.com/discord-jda/jda/wiki/9)-Webhooks

Shows how to construct and send a WebhookMessage with custom content, embeds, and a username using WebhookMessageBuilder and WebhookClient.

```java
WebhookMessageBuilder builder = new WebhookMessageBuilder();
builder.setContent("This is a normal message content");
MessageEmbed firstEmbed = new EmbedBuilder().setColor(Color.RED).setDescription("This is one embed").build();
MessageEmbed secondEmbed = new EmbedBuilder().setColor(Color.GREEN).setDescription("This is another embed").build();
builder.addEmbeds(firstEmbed, secondEmbed)
       .setUsername("Minn");
WebhookMessage message = builder.build();
client.send(message);
```

--------------------------------

### Close WebhookClient

Source: https://github.com/discord-jda/jda/wiki/9)-Webhooks

Illustrates the recommended way to close a WebhookClient instance after it is no longer needed to release resources.

```java
client.close();
```