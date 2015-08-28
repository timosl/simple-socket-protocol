# Simple Socket Protocol

The Simple Socket Protocol is a high level networking library for Java, that uses WebSocket connections to transmit messages between clients and a server. Simple Java objects can be sent and callback methods defined to trigger an action when an object of a given class is received.

This library is intended for applications that need a simple mechanism for transmitting Java objects between a client and a server. If you need more control over the connection, this library is not for you. Try the [Java WebSockets](https://github.com/TooTallNate/Java-WebSocket) project instead.

## Build and Installation

The project uses Maven:
```xml
<dependency>
    <groupId>de.timosl</groupId>
    <artifactId>simple-socket-protocol</artifactId>
    <version>0.1.0</version>
</dependency>
```
If the project is not available in a public Maven repository, clone the project and perform ```mvn install``` inside the projects root directory. This will install the library on your machine.

## Minimum Reqirements

This project requires **Java 1.5**. You can also use it on Android, which requires **Android 1.6** (API 4).

## Dependencies

This project makes use of [Java WebSocket](https://github.com/TooTallNate/Java-WebSocket) and [google-gson](https://github.com/google/gson). If you have some time to spare, take a minute to check out their repositories.

## Usage

### Server

Create a new instance of the server and start it:
```java
SocketServer server = new SocketServer(new InetSocketAddress("localhost", 9090));
server.start();
```
If you don't need the server anymore, simply call:
```java
server.stop();
```
To react to incoming messages from clients, simply define a callback for the object class that you want to handle:
```java
server.registerEventListener(ExampleObject.class, new ServerEventListener() {
    public PendingEvent onMessageReceived(String clientID, Object receivedEvent) {
        // Handle the event
    }
});
```
The client ID will be a String, uniquely identifying the remote client. The event object will be of the class that you declared in the ```registerEventListener()``` method (you have to cast it manually though).

## Client

Create a new instance of the client and start it:
```java
SocketClient client = new SocketClient(new URI("ws://localhost:9090"));
client.connect();
```
The ```connect()``` method is non-blocking and the client will try to connect in the background. If you want to be informed about the connection state of the client, you can register a listener for it:
```java
client.addSocketClientStateListener(new StateListener() {
    public void onConnectionReady() {
        // Called when the client is connected
    }
    public void onConnectionError(Exception exception) {
        // Called when an error occurred
    }
    public void onConnectionClosed() {
       // Called when the connection is closed
    }
});
```
If you don't need the client anymore, simply call:
```java
client.disconnect();
```
To react to incoming messages from clients, simply define a callback for the object class that you want to handle:
```java
client.registerEventListener(String.class, new ClientEventListener() {
    public PendingEvent onMessageReceived(Object receivedEvent) {
        // Handle the event
    }
});
```
The event object will be of the class that you declared in the ```registerEventListener()``` method (you have to cast it manually though).

## Sending Messages

Both the server and the client can send messages and can use callback methods to handle certain events.
```java
ResultHandler defaultHandler = new ResultHandler() {
    public void onTimeout() {
        // Called when the message was not delivered because the connection
        // timed out or the remote end did not acknowledge the message
    }
    public PendingEvent onResponseReceived(Object receivedEvent) {
        // Called when the remote end responded with a message of its own
    }
    public PendingEvent onError(SocketError error) {
        // Called when an error occured on the remote end
    }
};
```
While the client can only send to the server it is connected to, the server can either send to one specific client by using the ```send()``` method or broadcasting a message to all currently connected clients
by using the ```sendBroadcast()``` method.

A client or server can send a response to an incoming message by returning a ```PendingEvent``` object inside the ```onMessageReceived()``` method or inside a ```ResultHandler``` object. If you return ```null``` instead, a simple acknowledge message is sent. See the JavaDoc for these classes and methods to learn more.

## License

This project is licensed under the MIT License. Check the **LICENSE** file in the projects root folder for more details.