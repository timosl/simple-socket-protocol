package de.timosl.ssp.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;

import com.google.gson.Gson;

import de.timosl.ssp.ResultHandler;
import de.timosl.ssp.models.PendingEvent;
import de.timosl.ssp.models.PendingMessage;
import de.timosl.ssp.models.SocketAck;
import de.timosl.ssp.models.SocketError;
import de.timosl.ssp.models.SocketMessage;

/**
 * The MessageHandling class is used by {@link SocketClient} and {@link SocketServer}
 * to transmit messages to the remote end, react to incoming messages and to perform
 * actions of any {@link ResultHandler} and {@link InternalEventListener}. {@link SocketMessage}s
 * should therefore not be send directly, but rather passed to this object, so it can
 * properly track the state of each message and their corresponding response events. 
 * <br><br>
 * There is also a default built-in EventListener that will catch any incoming events that have
 * no EventListener associated with them. This listener will print an error message to stderr
 * and return a {@link SocketError} message to the remote end. This default listener can be
 * set to a custom implementation as well.
 *
 */
class MessageHandling {
	
	/**
	 * The timeout (in milliseconds) after which a {@link SocketMessage} will be considered
	 * 'timed-out' if no response is received for it from the remote end. If this limit is reached,
	 * the {@link ResultHandler#onTimeout()} method will be called on the associated
	 * {@link ResultHandler}.
	 */
	private long messageTimeout = 5000L;
	
	/**
	 * A {@link Gson} instance for converting objects from and to JSON.
	 */
	private Gson gson = new Gson();
	
	/**
	 * An {@link ExecutorService} to schedule events.
	 */
	private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	
	/**
	 * Maps the message IDs to the {@link PendingMessage} containing the original {@link SocketMessage}
	 * and the associated {@link ResultHandler}.
	 */
	private Map<String, PendingMessage> pendingMessages = new HashMap<String,PendingMessage>();
	
	/**
	 * The {@link InternalEventListener} to call when a new incoming message has been received.
	 */
	private InternalEventListener eventListener;
	
	/**
	 * Creates a new {@link MessageHandling} instance with the given {@link InternalEventListener}.
	 * 
	 * @param listener
	 */
	public MessageHandling(InternalEventListener listener)  {
		this.eventListener = listener;
	}
	
	/**
	 * Sets the time after which messages will be considered 'timed-out' and
	 * the {@link ResultHandler#onTimeout()} method will be called on the
	 * associated {@link ResultHandler}.
	 * 
	 * @param timeout The timout in milliseconds
	 */
	public void setMessageTimeout(long timeout) {
		this.messageTimeout = timeout;
	}
	
	/**
	 * Returns the time after which messages will be considered 'timed-out' and
	 * the {@link ResultHandler#onTimeout()} method will be called on the
	 * associated {@link ResultHandler}.
	 * 
	 * @return The timout in milliseconds
	 */
	public long getMessageTimeout() {
		return this.messageTimeout;
	}
	
	/**
	 * Generates a new {@link SocketMessage} with the given event and optionally
	 * as a response to the given SocketMessage.
	 * 
	 * @param event The event to wrap into this {@link SocketMessage}
	 * @param originalMessage The {@link SocketMessage} this message is in response to
	 * @return The {@link SocketMessage} that wraps the given event
	 */
	public SocketMessage generateMessage(Object event, SocketMessage originalMessage) {
		SocketMessage message = new SocketMessage();
		message.eventClass = event.getClass().getCanonicalName();
		message.eventContent = new Gson().toJson(event);
		message.messageID = UUID.randomUUID().toString();
		message.messageReplyID = (originalMessage != null) ? originalMessage.messageID : null;
		message.timestamp = System.currentTimeMillis();
		return message;
	}
	
	/**
	 * Extracts an event from a given {@link SocketMessage}.
	 * 
	 * @param message The {@link SocketMessage} to extract the event from
	 * @return The event wrapped in the given {@link SocketMessage}
	 */
	public static Object extractEvent(SocketMessage message) {
		// Construct the class wrapped by this SocketMessage
		Class<?> receivedClass;
		
		// Try to create a class instance from the given class name
		try {
			receivedClass = MessageHandling.class.getClassLoader().loadClass(message.eventClass);
		} catch (ClassNotFoundException e) {
			System.err.println("Invalid class: "+message.eventClass);
			return null;
		}
				
		// Cast it to the SocketEvent class
		return new Gson().fromJson(message.eventContent, receivedClass);
	}
	
	/**
	 * Passes the given {@link SocketMessage} to the {@link MessageHandling}
	 * and calls the given {@link ResultHandler} when a response for it is
	 * received.
	 * 
	 * @param connection The {@link WebSocket} connection this message will be sent over
	 * @param message The {@link SocketMessage} to sent
	 * @param handler The {@link ResultHandler} to call when a response for this message is received
	 */
	public void transmitMessage(WebSocket connection, SocketMessage message, ResultHandler handler) {
		// Abort early if we cannot send the message
		if(connection == null || message == null) {
			System.err.println("Cannot send invalid message");
			return;
		}
		
		// Prepare a PendingMessage that contains the given SocketMessage and ResultHandler
		PendingMessage pend = new PendingMessage(message, handler);
		
		// Place it in the map for later retrieval
		synchronized (this.pendingMessages) {
			this.pendingMessages.put(message.messageID, pend);
		}
		
		// Try to send the message. If it fails, just keep it in the
		// map, so the timeout will be reached eventually and the calling
		// application can perform appropriate steps for solving this
		// problem
		try {
			connection.send(this.gson.toJson(message));
		} catch(Exception e) {
			System.err.println("There was an error sending the message. " +
					"The error was: '"+e+"'\nThe message was NOT send");
		}		
		
		// Schedule the timeout
		TimeoutTask timeoutTask = new TimeoutTask(message.messageID);
		this.scheduler.schedule(timeoutTask, messageTimeout, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Call this when a {@link SocketMessage} has been received on a {@link WebSocket}
	 * connection. If this message is a result to a message that has been
	 * sent previously by this object, the stored {@link ResultHandler}
	 * will be called. If this message is initiated by the remote end, the
	 * {@link InternalEventListener} registered for the events class will be called.
	 * 
	 * @param clientID The unique ID of the client associated with the given connection
	 * @param connection The {@link SocketMessage} connection the event was received on
	 * @param message The received {@link SocketMessage}
	 */
	public void messageReceived(String clientID, WebSocket connection, SocketMessage message) {		
		// Retrieve the pending message for the incoming message
		PendingMessage pend = null;
		synchronized (this.pendingMessages) {
			pend = this.pendingMessages.get(message.messageReplyID);
			this.pendingMessages.remove(message.messageReplyID);
		}
		
		// Check if there was a handler stored for this response
		if(pend == null) {
			// No pending message, it's either initiated from
			// the remote end, or it's a broken message
			if(message.messageReplyID == null) {
				// It's initiated from the remote end, handle
				// it with the appropriate listener
				this.handleIncoming(clientID, connection, message);
			} else {
				System.err.println("Received a message that was in reply to a message we don't have anymore");
			}
		} else {
			// There was a handler registered for this message
			// so we can pass the result to it
			this.handleResponse(connection, message, pend);
		}
	}
	
	/**
	 * A task that will call the {@link ResultHandler#onTimeout()} method
	 * after the set timeout interval. If the message was already handled
	 * (because a response was received) no action will be performed.
	 *
	 */
	private class TimeoutTask implements Runnable {
		
		/**
		 * The ID of the message associated with this {@link TimeoutTask}. 
		 */
		private String messageID;
		
		/**
		 * Creates a new {@link TimeoutTask} for the {@link SocketMessage}
		 * with the given ID.
		 * 
		 * @param messageID The ID of the {@link SocketMessage}
		 */
		TimeoutTask(String messageID) {
			this.messageID = messageID;
		}
		
		public void run() {
			// If the messageID is still pending, we have to perform the timeout task
			synchronized (pendingMessages) {
				PendingMessage message = pendingMessages.get(this.messageID);
				pendingMessages.remove(this.messageID);
				message.handler.onTimeout();
			}
		}
	}
	
	/**
	 * Handle a {@link SocketMessage} that is a response to a previously sent message.
	 * 
	 * @param connection The {@link SocketMessage} connection the event was received on
	 * @param message The received {@link SocketMessage}
	 * @param pend The {@link PendingMessage} that contains the {@link ResultHandler} for this {@link SocketMessage}
	 */
	private void handleResponse(WebSocket connection, SocketMessage message, PendingMessage pend) {
		// Get the event object from the message
		Object receivedEvent = extractEvent(message);
		
		// Return if the message is empty or there
		// is no handler for it
		if(receivedEvent == null) {
			return;
		}
		
		// If we have no handler stored for this message, just send a generic acknowledgment
		if(pend.handler == null) {
			// But only if we didn't receive an Ack (don't answer Acks with Acks, 
			// or we get a loop)
			if(!(receivedEvent instanceof SocketAck)) {
				SocketMessage responseMessage = generateMessage(new SocketAck(),message);
				this.transmitMessage(connection, responseMessage, null);
			}
			return;
		}
		
		// If this is set, the handler wants to respond to
		// this response
		PendingEvent response = null;
		
		// Check if it's an error of a normal message
		if(receivedEvent instanceof SocketError) {
			response = pend.handler.onError((SocketError) receivedEvent);
		} else {
			response = pend.handler.onResponseReceived(receivedEvent);
		}
		
		// The handler wants to send a response
		if(response != null) {
			SocketMessage responseMessage = generateMessage(response.event,message);
			this.transmitMessage(connection, responseMessage, response.handler);
		} else {
			// The listener doesn't want to respond, so we send a generic acknowledgement.
			// But only if we received something other than an Ack (don't answer Acks with Acks, 
			// or we get a loop)
			if(!(receivedEvent instanceof SocketAck)) {
				SocketMessage responseMessage = generateMessage(new SocketAck(),message);
				this.transmitMessage(connection, responseMessage, null);
			}		
		}
	}
	
	/**
	 * Handle a {@link SocketMessage} that was initiated from the remote end.
	 * 
	 * @param clientID The unique ID of the client associated with the given connection
	 * @param connection The {@link SocketMessage} connection the event was received on
	 * @param message The received {@link SocketMessage}
	 */
	private void handleIncoming(String clientID, WebSocket connection, SocketMessage message) {		
		// Forward the event to the event listener. May return 'null' or a reponse message
		PendingEvent response = this.eventListener.onMessageReceived(clientID, message, extractEvent(message));
		
		// Check if the listener wants to respond
		if(response != null) {
			// The listener wants to respond
			SocketMessage responseMessage = generateMessage(response.event,message);
			this.transmitMessage(connection, responseMessage, response.handler);
		} else {
			// The listener doesn't want to respond, so we send a generic acknowledgement
			SocketMessage responseMessage = generateMessage(new SocketAck(),message);
			this.transmitMessage(connection, responseMessage, null);
		}
	}
}
