package de.timosl.ssp.internal;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.framing.Framedata.Opcode;
import org.java_websocket.framing.FramedataImpl1;

import de.timosl.ssp.ResultHandler;
import de.timosl.ssp.ServerEventListener;
import de.timosl.ssp.models.PendingEvent;
import de.timosl.ssp.models.SocketMessage;

/**
 * Server end of the protocol. Use the {@link #sendBroadcast(Object, ResultHandler)} or
 * {@link #send(String, Object, ResultHandler)} method to deliver messages to clients.
 * Register {@link InternalEventListener}s to respond to messages received from the clients.
 *
 */
public class SocketServer implements InternalEventListener {
	
	/**
	 * A bundle of a {@link WebSocket} connection and
	 * the associated client identifier.
	 *
	 */
	private static class RemoteConnection {
		
		/**
		 * The {@link WebSocket} connection to a remote client. 
		 */
		WebSocket socket;
		
		/**
		 * The unique identifier for the connection to the remote client.
		 */
		String identifier;
	}
	
	/**
	 * The delay after which the connection is considered dead and will be closed.
	 */
	private long timeoutDelay = 240000L; // 4 minutes
	
	/**
	 * Interval at which check the last activity for all connections. This does not
	 * mean the interval at which actual PING messages are sent to the clients.
	 */
	private long pingInterval = 120000L; // 2 minutes
	
	/**
	 * The underlying {@link SocketServerInternal} instance.
	 */
	private SocketServerInternal server;
	
	/**
	 * A {@link ScheduledExecutorService} for scheduling tasks.
	 */
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	
	/**
	 * Task to check the activity of all current connections. This task will close
	 * connections if they are idle for too long, but will not send a PING command
	 * by itself.
	 */
	private ScheduledFuture<?> pingTask;
	
	/**
	 * The {@link MessageHandling} instance to handle incoming and outgoing messages.
	 */
	private MessageHandling messageHandling = new MessageHandling(this);
	
	/**
	 * The {@link ServerEventListener}s to call when a new event is received.
	 */
	private Map<String,ServerEventListener> listeners = new HashMap<String,ServerEventListener>();
	
	/**
	 * An {@link ArrayList} containing all currently connected {@link RemoteConnection}s.
	 */
	private ArrayList<RemoteConnection> connections = new ArrayList<RemoteConnection>();
	
	/**
	 * Creates a new {@link SocketServer}.
	 * 
	 * @param address The local socket address the server binds to
	 */
	public SocketServer(InetSocketAddress address) {
		this.server = new SocketServerInternal(this, address);
	}

	
	/**
	 * Starts the server so clients can connect to it.
	 */
	public void start() {
		this.server.start();
		this.pingTask = this.scheduler.scheduleAtFixedRate(new PingTask(), pingInterval, pingInterval, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Stops the server and clients can no longer connect to it.
	 */
	public void stop() {
		if(this.pingTask != null) {
			this.pingTask.cancel(true);
		}
		
		try {
			this.server.stop();
		} catch (Exception e) {
			// Ignore any exceptions since we stop
			// the server anyway
		}
	}	
	
	/**
	 * Broadcasts the given event to all currently connected clients.
	 * 
	 * @param event The event to broadcast
	 * @param handler The {@link ResultHandler} to be called for this event. Note that
	 * the handler will be called for each client the event is transmitted to
	 * @return The amount of clients that the event was transmitted to (regardless of whether they
	 * actually received it or were able to process it)
	 */
	public int sendBroadcast(Object event, ResultHandler handler) {
		// Generate the message
		SocketMessage message = this.messageHandling.generateMessage(event, null);
		
		// Send the message to everyone
		synchronized (this.connections) {
			for(RemoteConnection connection: this.connections) {
				this.messageHandling.transmitMessage(connection.socket, message, handler);
			}
			
			return this.connections.size();
		}		
	}

	/**
	 * Sends the given event object to the client with the given ID.
	 * 
	 * @param clientID The string object, uniquely identifying the remote client
	 * @param event The event that will be transmitted
	 * @param handler The {@link ResultHandler} to be called for this event
	 * @return Returns 'true' only, if the given client was online and
	 * the event was sent (regardless of whether the client actually received the event or
	 * was able to process it) and 'false' if the client was not online and the
	 * event was not transmitted
	 */
	public boolean send(final String clientID, final Object event, final ResultHandler handler) {
		// Generate the message
		SocketMessage message = this.messageHandling.generateMessage(event, null);
		
		// Get the current connection of the client
		WebSocket conn = null;
		synchronized (this.connections) {
			for(RemoteConnection connection: this.connections) {
				if(connection.identifier.equals(clientID)) {
					conn = connection.socket;
				}
			}
		}
		
		// Check if the client is online and has a connection with the server
		if(conn != null) {
			this.messageHandling.transmitMessage(conn, message, handler);
			return true;
		} else {
			this.scheduler.execute(new Runnable() {
				public void run() {
					// Wait some time before calling the onTimeout() method,
					// so we don't perform it before the original method
					// call has completed (hopefully)
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {}
					handler.onTimeout();
				}
			});			
			return false;
		}
	}

	/**
	 * Sends a PING message to all currently connected clients.
	 * 
	 * @return The amount of clients the PING message was sent to
	 */
	public int sendPing() {
		synchronized (this.connections) {
			for(RemoteConnection connection: this.connections) {
				FramedataImpl1 frame = new FramedataImpl1(Opcode.PING);
				frame.setFin(true);
				connection.socket.sendFrame(frame);
			}
			
			return this.connections.size();
		}		
	}
	
	/**
	 * Registers an {@link InternalEventListener} that will be called when an object
	 * of the given type is received from a client.
	 * 
	 * @param clazz The class of objects this handler will be called for
	 * @param listener The {@link InternalEventListener}
	 */
	public void registerEventListener(Class<?> clazz, ServerEventListener listener) {
		synchronized (this.listeners) {
			this.listeners.put(clazz.getCanonicalName(), listener);
		}
	}

	public PendingEvent onMessageReceived(String clientID, SocketMessage message, Object receivedEvent) {
		synchronized (this.listeners) {
			return this.listeners.get(receivedEvent.getClass().getCanonicalName()).onMessageReceived(clientID, receivedEvent);
		}
	}


	/**
	 * Called when a new connection was opened by the underlying {@link SocketServerInternal}.
	 * 
	 * @param conn The new {@link WebSocket} connection
	 */
	protected void onOpen(WebSocket conn) {
		synchronized (this.connections) {
			RemoteConnection connection = new RemoteConnection();
			connection.socket = conn;
			connection.identifier = UUID.randomUUID().toString();
			this.connections.add(connection);
		}
	}
	
	/**
	 * Called when a new connection was closed by the underlying {@link SocketServerInternal}.
	 * 
	 * @param conn The now closed {@link WebSocket} connection
	 */
	protected void onClose(WebSocket conn) {
		synchronized (this.connections) {
			// We have to find the corresponding RemoteConnection and remove it
			RemoteConnection connectionToClose = null;
			for(RemoteConnection connection: this.connections) {
				if(connection.socket == conn) {
					connectionToClose = connection;
				}
			}
			this.connections.remove(connectionToClose);
		}
	}
	
	/**
	 * Called when the underlying {@link SocketServerInternal} received a message.
	 * 
	 * @param conn The {@link WebSocket} connection the message was received on 
	 * @param message The received {@link SocketMessage}
	 */
	protected void onMessage(WebSocket conn, SocketMessage message) {
		synchronized (this.connections) {
			for(RemoteConnection connection: this.connections) {
				if(connection.socket == conn) {
					this.messageHandling.messageReceived(connection.identifier, connection.socket, message);
					break;
				}
			}
		}
					
	}
	
	/**
	 * Task to check the activity of all current connections. This task will close
	 * connections if they are idle for too long, but will not send a PING command
	 * by itself.
	 *
	 */
	private class PingTask implements Runnable {
		public void run() {		
			synchronized (SocketServer.this.connections) {
				for(RemoteConnection connection: SocketServer.this.connections) {
					if(server.getLastActivity(connection.socket) < System.currentTimeMillis() - timeoutDelay) {
						connection.socket.close();
					}
				}	
			}			
		}		
	}
}
