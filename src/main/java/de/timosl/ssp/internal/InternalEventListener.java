package de.timosl.ssp.internal;

import de.timosl.ssp.models.PendingEvent;
import de.timosl.ssp.models.SocketMessage;


/**
 * A listener that will be called when a new message
 * was received from the remote end of the connection
 * and is not to a response to a local message. This
 * is only to be called internally.
 *
 */
interface InternalEventListener {
	
	/**
	 * Called when a new message was received from the remote end.
	 * 
	 * @param clientID The unique ID of the client that sent the message
	 * @param message The received {@link SocketMessage}
	 * @param receivedEvent The received event
	 * @return Return a {@link PendingEvent} to deliver to the remote end
	 * in response to the received event or return 'null' to respond with
	 * the default response
	 */
	public PendingEvent onMessageReceived(String clientID, SocketMessage message, Object receivedEvent);
}
