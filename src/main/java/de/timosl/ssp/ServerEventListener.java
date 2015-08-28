package de.timosl.ssp;

import de.timosl.ssp.models.PendingEvent;

/**
 * A listener that will be called when a new message
 * was received from the remote end of the connection
 * and is not to a response to a local message.
 *
 */
public interface ServerEventListener {
	
	/**
	 * Called when a new message was received from the remote end.
	 * 
	 * @param connectionID A String object uniquely identifying the client, which sent the event
	 * @param receivedEvent The received event
	 * @return Return a {@link PendingEvent} to deliver to the remote end
	 * in response to the received event or return 'null' to respond with
	 * the default response
	 */
	public PendingEvent onMessageReceived(String connectionID, Object receivedEvent);
}
