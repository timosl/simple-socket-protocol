package de.timosl.ssp.internal;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.java_websocket.framing.Framedata.Opcode;
import org.java_websocket.framing.FramedataImpl1;

import de.timosl.ssp.ClientEventListener;
import de.timosl.ssp.ResultHandler;
import de.timosl.ssp.StateListener;
import de.timosl.ssp.models.PendingEvent;
import de.timosl.ssp.models.SocketMessage;

/**
 * Client end of the protocol. Use the {@link #sendEvent(Object, ResultHandler)} method
 * to deliver events to the server. Register {@link InternalEventListener} and {@link StateListener}
 * to respond to incoming messages or connection state changes.
 *
 */
public class SocketClient implements InternalEventListener {
	
	/**
	 * The {@link URI} of the server.
	 */
	private URI serverURI;
	
	/**
	 * The delay after which the connection is considered dead and will be closed.
	 */
	private long timeoutDelay = 240000L; // 4 minutes by default
	
	/**
	 * Interval at which check the last activity for the current connection. This does not
	 * mean the interval at which actual PING messages are sent to the server.
	 */
	private long pingInterval = 120000L; // 2 minutes by default
	
	/**
	 * The underlying {@link SocketClientInternal} instance.
	 */
	private SocketClientInternal client;
	
	/**
	 * An {@link ExecutorService} to schedule events.
	 */
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	
	/**
	 * Task to check the activity of the current connection. This task will close
	 * the connection if it is idle for too long, but will not send a PING command
	 * by itself.
	 */
	private ScheduledFuture<?> pingTask;
	
	/**
	 * The Set of {@link StateListener}s to inform of connection state changes.
	 */
	private Set<StateListener> stateListeners = new HashSet<StateListener>();
	
	/**
	 * The {@link MessageHandling} instance to handle incoming and outgoing messages.
	 */
	private MessageHandling messageHandling = new MessageHandling(this);
	
	/**
	 * The {@link ClientEventListener}s to call when a new event is received.
	 */
	private Map<String,ClientEventListener> listeners = new HashMap<String,ClientEventListener>();
	
	/**
	 * Creates a new {@link SocketClient} instance using the default
	 * timeout values.
	 * 
	 * @param serverURI The {@link URI} of the server
	 */
	public SocketClient(URI serverURI) {
		this.serverURI = serverURI;
		this.pingTask = this.scheduler.scheduleAtFixedRate(new PingTask(), this.pingInterval, this.pingInterval, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Creates a new {@link SocketClient} instance.
	 * 
	 * @param serverURI The {@link URI} of the server
	 * @param timeoutDelay The delay after which the connection is considered dead and will be closed
	 * @param pingInterval Interval at which to send a PING message to the server
	 */
	public SocketClient(URI serverURI, long timeoutDelay, long pingInterval) {
		this.serverURI = serverURI;
		this.timeoutDelay = timeoutDelay;
		this.pingInterval = pingInterval;
		this.pingTask = this.scheduler.scheduleAtFixedRate(new PingTask(), this.pingInterval, this.pingInterval, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Connects the client to the server.
	 */
	public void connect() {
		this.client = new SocketClientInternal(this, serverURI);
		this.client.connect();
	}
	
	/**
	 * Disconnects the client.
	 */
	public void disconnect() {
		if(this.pingTask != null) this.pingTask.cancel(true);
		this.client.close();
	}
	
	/**
	 * Sends the given event to the server. The given {@link ResultHandler} will
	 * be called when the server responds to this message.
	 * 
	 * @param event The event object to send
	 * @param handler The {@link ResultHandler} to call when the server responds
	 * to this message
	 */
	public void sendEvent(Object event, ResultHandler handler) {
		SocketMessage message = this.messageHandling.generateMessage(event, null);
		this.messageHandling.transmitMessage(this.client.getConnection(), message, handler);
	}
	
	/**
	 * Sends a PING message to the server.
	 */
	public void sendPing() {
		// Don't send a ping if the connection is not ready
		if(this.client == null || !this.client.getConnection().isOpen()) {
			return;
		}
		
		// Construct the PING frame
		FramedataImpl1 frame = new FramedataImpl1(Opcode.PING);
		frame.setFin(true);
		this.client.getConnection().sendFrame(frame);
	}

	/**
	 * Registers an {@link ClientEventListener} that will be called when an object
	 * of the given type is received from the server.
	 * 
	 * @param clazz The class of objects this handler will be called for
	 * @param listener The {@link ClientEventListener}
	 */
	public void registerEventListener(Class<?> clazz, ClientEventListener listener) {
		synchronized (this.listeners) {
			this.listeners.put(clazz.getCanonicalName(), listener);
		}
	}
	
	/**
	 * Removes the {@link ClientEventListener} registered for the given class.
	 * The listener will no longer be called when such a message is received and instead,
	 * an error message will be returned to the remote end.
	 * 
	 * @param clazz The class of objects the handler would be called for
	 */
	public void removeEventListener(Class<?> clazz) {
		synchronized (this.listeners) {
			this.listeners.remove(clazz.getCanonicalName());
		}
	}

	/**
	 * Adds a {@link StateListener} that will be called when the
	 * state of the connection changes.
	 * 
	 * @param listener The {@link StateListener} to add
	 */
	public void addSocketClientStateListener(StateListener listener) {
		synchronized (this.stateListeners) {
			this.stateListeners.add(listener);
		}
	}

	/**
	 * Removes the given {@link StateListener}. It will no longer
	 * be called when the state of the connection changes.
	 * 
	 * @param listener
	 */
	public void removeSocketClientStateListener(StateListener listener) {
		synchronized (this.stateListeners) {
			this.stateListeners.remove(listener);
		}
	}

	public PendingEvent onMessageReceived(String clientID, SocketMessage message, Object receivedEvent) {
		synchronized (this.listeners) {
			return this.listeners.get(receivedEvent.getClass().getCanonicalName()).onMessageReceived(receivedEvent);
		}
	}

	/**
	 * Called when the underlying {@link SocketClientInternal} has successfully opened
	 * the connection.
	 */
	protected void connectionReady() {
		this.notifyListenersReady();
	}

	/**
	 * Called when the underlying {@link SocketClientInternal} received a message.
	 * 
	 * @param message The message received by the {@link SocketClientInternal}
	 */
	protected void receivedMessage(SocketMessage message) {
		this.messageHandling.messageReceived(null, this.client.getConnection(), message);
	}
	
	/**
	 * Called when the underlying {@link SocketClientInternal} encountered an error.
	 * 
	 * @param exception The received error
	 */
	protected void receivedError(Exception exception) {
		this.notifyListenersError(exception);
	}
	
	/**
	 * Called when the underlying {@link SocketClientInternal} closed the connection.
	 */
	protected void receivedClose() {
		this.notifyListenersClosed();
	}

	/**
	 * Notifies all {@link StateListener}s of the given error.
	 * 
	 * @param exception The error that occurred
	 */
	private void notifyListenersError(Exception exception) {
		synchronized (this.stateListeners) {
			for(StateListener listener: this.stateListeners) {
				listener.onConnectionError(exception);
			}
		}
	}
	
	/**
	 * Notifies all {@link StateListener}s that the connection is now
	 * ready.
	 */
	private void notifyListenersReady() {
		synchronized (this.stateListeners) {
			for(StateListener listener: this.stateListeners) {
				listener.onConnectionReady();
			}
		}
	}
	
	/**
	 * Notifies all {@link StateListener}s that the connection is now
	 * closed.
	 */
	private void notifyListenersClosed() {
		synchronized (this.stateListeners) {
			for(StateListener listener: this.stateListeners) {
				listener.onConnectionClosed();
			}
		}
	}
	
	/**
	 * Task to check if the connection was inactive for too long.
	 * 
	 */
	private class PingTask implements Runnable {
		public void run() {
			if(client.getLastActivity() < System.currentTimeMillis() - timeoutDelay) {
				client.close();
			}
		}		
	}
}
