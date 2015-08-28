package de.timosl.ssp;

import de.timosl.ssp.models.PendingEvent;
import de.timosl.ssp.models.SocketError;

/**
 * The ResultHandler is called when a response has been received
 * from the remote end. You can deliver a response to this event
 * by returning a {@link PendingEvent}.
 *
 */
public interface ResultHandler {
	
	/**
	 * Called when the remote end was able to process the previous
	 * message and returned a response.
	 * 
	 * @param receivedEvent The event received with this response
	 * @return Return a {@link PendingEvent} to deliver to the remote end
	 * in response to the received event or return 'null' to respond with
	 * the default response
	 */
	public PendingEvent onResponseReceived(Object receivedEvent);
	
	/**
	 * Called when the remote end returned an error while processing
	 * the previously sent message.
	 * 
	 * @param error The {@link SocketError} describing the reasons for the error
	 * @return Return a {@link PendingEvent} to deliver to the remote end
	 * in response to the received event or return 'null' to respond with
	 * the default response
	 */
	public PendingEvent onError(SocketError error);
	
	/**
	 * Called when the remote end did not respond to the previous message in time.
	 */
	public void onTimeout();
}
