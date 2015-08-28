package de.timosl.ssp.models;

import de.timosl.ssp.ResultHandler;

/**
 * Combines an event to be transmitted with the {@link ResultHandler}
 * to be called when the response for it arrives.
 *
 */
public class PendingEvent {
	
	/**
	 * The event to be transmitted over the network.
	 */
	public Object event;
	
	/**
	 * The {@link ResultHandler} to be called when 
	 * the response for the {@link #event} arrives.
	 */
	public ResultHandler handler;
	
	/**
	 * Creates a new, empty {@link PendingEvent}.
	 */
	public PendingEvent() {}
	
	/**
	 * Creates a new {@link PendingEvent} with the given
	 * parameters.
	 * 
	 * @param event The event to be transmitted over the network
	 * @param handler The {@link ResultHandler} to be called when 
	 * the response for the event arrives
	 */
	public PendingEvent(Object event, ResultHandler handler) {
		this.event = event;
		this.handler = handler;
	}
}
