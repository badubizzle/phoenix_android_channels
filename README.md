# phoenix_android_channels
Pheonix android channel (websocket) client based on https://github.com/eoinsha/JavaPhoenixChannels and https://github.com/koush/AndroidAsync

The original lib (https://github.com/eoinsha/JavaPhoenixChannels) uses com.squareup.okhttp:okhttp
which was giving me issues so I substituted https://github.com/koush/AndroidAsync for handling the socket connection

I added ISocket Interface to allow you to plug the new socket implementation easily into existing code.

All other files remain same as from the original lib, more or less.

## Example using Java
```java
import org.phoenixframework.channels.*;

ISocket socket;
Channel channel;

socket = new Socket("ws://localhost:4000/socket/websocket");
socket.connect();

channel = socket.chan("rooms:lobby", null);

channel.join()
.receive("ignore", new IMessageCallback() {
    @Override
    public void onMessage(Envelope envelope) {
        System.out.println("IGNORE");
    }
})
.receive("ok", new IMessageCallback() {
    @Override
    public void onMessage(Envelope envelope) {
        System.out.println("JOINED with " + envelope.toString());
    }
});

channel.on("new:msg", new IMessageCallback() {
    @Override
    public void onMessage(Envelope envelope) {
        System.out.println("NEW MESSAGE: " + envelope.toString());
    }
});

channel.onClose(new IMessageCallback() {
    @Override
    public void onMessage(Envelope envelope) {
        System.out.println("CLOSED: " + envelope.toString());
    }
});

channel.onError(new IErrorCallback() {
    @Override
    public void onError(String reason) {
        System.out.println("ERROR: " + reason);
    }
});

//Sending a message. This library uses Jackson for JSON serialization
ObjectNode node = new ObjectNode(JsonNodeFactory.instance)
        .put("user", "my_username")
        .put("body", message);

channel.push("new:msg", node);
```