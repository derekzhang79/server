package org.ohmage.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.joda.time.DateTime;
import org.ohmage.annotator.Annotator.ErrorCode;
import org.ohmage.domain.DataStream;
import org.ohmage.domain.DataStream.MetaData;
import org.ohmage.domain.Observer;
import org.ohmage.domain.Observer.Stream;
import org.ohmage.exception.DataAccessException;
import org.ohmage.exception.DomainException;
import org.ohmage.exception.ServiceException;
import org.ohmage.query.IObserverQueries;

/**
 * <p>
 * This class provides all of the services necessary for reading, writing, and
 * manipulating observers, streams, and their data.
 * </p>
 *
 * @author John Jenkins
 */
public class ObserverServices {
	private static final Logger LOGGER = 
		Logger.getLogger(ObserverServices.class);
	
	/**
	 * An invalid point in an upload. This represents the index in the upload
	 * array, the data for that index, the reason it was rejected, and, 
	 * optionally, an exception which triggered the rejection.
	 *
	 * @author John Jenkins
	 */
	@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
	public static class InvalidPoint {
		/**
		 * The JSON key for the invalid point's index.
		 */
		public static final String JSON_KEY_INDEX = "index";
		/**
		 * The JSON key for the invalid point's data.
		 */
		public static final String JSON_KEY_DATA = "data";
		/**
		 * The JSON key for the reason the invalid point was rejected.
		 */
		public static final String JSON_KEY_REASON = "reason";
		
		/**
		 * The invalid point's index.
		 */
		@JsonProperty(JSON_KEY_INDEX)
		private final long index;
		/**
		 * The invalid point's data.
		 */
		@JsonProperty(JSON_KEY_DATA)
		private final String data;
		/**
		 * The reason the invalid point was rejected.
		 */
		@JsonProperty(JSON_KEY_REASON)
		private final String reason;
		/**
		 * Any exception that may have been thrown when this point was
		 * rejected.
		 */
		@JsonIgnore
		private final Throwable cause;
		
		/**
		 * Creates an invalid point with the necessary parameters.
		 * 
		 * @param index The index in the upload array.
		 * 
		 * @param data The data for the index as a string.
		 * 
		 * @param reason A user-friendly string representing why this point was
		 * 				 invalid.
		 * 
		 * @param cause An optional exception which may have been thrown to
		 * 				cause the error.
		 */
		public InvalidPoint(
			final long index, 
			final String data, 
			final String reason, 
			final Throwable cause) {
			
			this.index = index;
			this.data = data;
			this.reason = reason;
			this.cause = cause;
		}

		/**
		 * Returns the index.
		 *
		 * @return The index.
		 */
		public long getIndex() {
			return index;
		}

		/**
		 * Returns the data.
		 *
		 * @return The data.
		 */
		public String getData() {
			return data;
		}

		/**
		 * Returns the reason.
		 *
		 * @return The reason.
		 */
		public String getReason() {
			return reason;
		}

		/**
		 * Returns the cause.
		 *
		 * @return The cause.
		 */
		public Throwable getCause() {
			return cause;
		}
	}
	
	private static ObserverServices instance;
	private IObserverQueries observerQueries;
	
	/**
	 * Default constructor. Privately instantiated via dependency injection
	 * (reflection).
	 * 
	 * @throws IllegalStateException if an instance of this class already
	 * exists
	 * 
	 * @throws IllegalArgumentException if iObserverQueries is null
	 */
	private ObserverServices(final IObserverQueries iObserverQueries) {
		if(instance != null) {
			throw new IllegalStateException("An instance of this class already exists.");
		}
		
		if(iObserverQueries == null) {
			throw new IllegalArgumentException("An instance of IObserverQueries is required.");
		}
		
		observerQueries = iObserverQueries;
		instance = this;
	}
	
	/**
	 * The instance of this service.
	 * 
	 * @return  Returns the singleton instance of this class.
	 */
	public static ObserverServices instance() {
		return instance;
	}
	
	/**
	 * Creates a new observer in the system and associates it with a user.
	 * 
	 * @param 
	 * 
	 * @param observer The observer.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void createObserver(
			final String username,
			final Observer observer) 
			throws ServiceException {
		
		try {
			observerQueries.createObserver(username, observer);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a user is allowed to create an observer.
	 * 
	 * @param username The user's username.
	 * 
	 * @param observerId The observer's unique identifier.
	 * 
	 * @throws ServiceException The user is not allowed to create the observer
	 * 							or there was an error.
	 */
	public void verifyUserCanCreateObserver(
			final String username,
			final String observerId)
			throws ServiceException {
		
		try {
			// First, the observer cannot already exist.
			if(observerQueries.doesObserverExist(observerId)) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INSUFFICIENT_PERMISSIONS,
					"An observer with the given ID already exists.");
			}
			
			// Other than that, anyone is allowed to create them.
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a user is allowed to update an observer.
	 * 
	 * @param username The user's username.
	 * 
	 * @param observerId The observer's unique identifier.
	 * 
	 * @throws ServiceException The user is not allowed to update the observer
	 * 							or there was an error.
	 */
	public void verifyUserCanUpdateObserver(
			final String username,
			final String observerId)
			throws ServiceException {
		
		try {
			String owner = observerQueries.getOwner(observerId);
			// If there is no owner, that is because the stream doesn't exist.
			if(owner == null) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INSUFFICIENT_PERMISSIONS,
					"An observer with the given ID does not exist.");
			}
			// If the requester is not the owner, then they do not have 
			// permission to update it.
			// If we open up the ACLs to allow others to update the observer,
			// this check would be altered and this is where those ACLs would
			// be applied.
			else if(! owner.equals(username)) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INSUFFICIENT_PERMISSIONS,
					"The requester is not the owner of the observer.");
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that a new observer conforms to the requirements of the 
	 * existing observer.
	 * 
	 * @param observer The observer that must, at the very least, have an
	 * 				   increased version number and may contain other fixes.
	 * 
	 * @throws ServiceException The observer is invalid as a new observer or
	 * 							there was an error.
	 */
	public Map<String, Long> verifyNewObserverIsValid(
			final Observer observer)
			throws ServiceException {
		
		if(observer == null) {
			throw new ServiceException(
				"The observer is null.");
		}
		
		try {
			// Compare the observer versions. If the new version is less than
			// or equal to the existing version, then this is not a valid 
			// update attempt.
			String observerId = observer.getId();
			if(observer.getVersion() <= 
				observerQueries.getGreatestObserverVersion(observerId)) {
				
				throw new ServiceException(
					ErrorCode.OBSERVER_INVALID_VERSION,
					"The new observer's version must increase: " +
						observer.getVersion());
			}
			
			// The set of stream IDs whose version did not increase. 
			Map<String, Long> result = new HashMap<String, Long>();
			
			// Compare each of the streams.
			for(Stream stream : observer.getStreamsMap().values()) {
				// Get the stream's version.
				Long streamVersion =
					observerQueries.getGreatestStreamVersion(
						observer.getId(), 
						stream.getId());
				
				// Get the new stream's version.
				long newStreamVersion = stream.getVersion();
				
				// If the stream didn't exist before, it is fine.
				if(streamVersion == null) {
					continue;
				}
				// If the new stream's version is less than the existing 
				// stream's version, that is an error.
				else if(newStreamVersion < streamVersion) {
					throw new ServiceException(
						ErrorCode.OBSERVER_INVALID_STREAM_VERSION,
						"The version of this stream, '" +
							stream.getId() +
							"', is less than the existing stream's version, '" +
							streamVersion +
							"': " + stream.getVersion());
				}
				// If the version didn't change, we add it to the set of stream
				// IDs.
				else if(newStreamVersion == streamVersion) {
					result.put(stream.getId(), streamVersion);
				}
				// Otherwise, the stream ID increased, in which case a new 
				// stream entry will be created.
			}
			
			return result;
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Verifies that the user is the owner of an observer.
	 * 
	 * @param username
	 *        The user's username.
	 * 
	 * @param observerId
	 *        The observer's unique identifier.
	 * 
	 * @param observerVersion
	 *        The observer's version.
	 * 
	 * @throws ServiceException
	 *         The user is not the owner of the observer or there was an issue
	 *         querying the database.
	 */
	public void verifyUserOwnsObserver(
		final String username,
		final String observerId)
		throws ServiceException {
		
		try {
			// Get the owner.
			String owner = observerQueries.getOwner(observerId);
			
			// If the owner doesn't exist, throw an exception.
			if(owner == null) {
				throw
					new ServiceException(
						ErrorCode.OBSERVER_INVALID_ID,
						"An observer with this ID does not exist: " +
							observerId);
			}
			
			if(! owner.equals(username)) {
				throw
					new ServiceException(
						ErrorCode.OBSERVER_INSUFFICIENT_PERMISSIONS,
						"The requesting user does not have sufficient " +
							"permission to modify the observer: " +
							observerId);
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the observer.
	 * 
	 * @param observerId The observer's unique identifier.
	 * 
	 * @param observerVersion The observer's version.
	 * 
	 * @return The observer.
	 * 
	 * @throws ServiceException The observer doesn't exist.
	 */
	public Observer getObserver(
			final String observerId,
			final Long observerVersion) 
			throws ServiceException {
		
		try {
			Collection<Observer> result = 
				observerQueries
					.getObservers(observerId, observerVersion, 0, 2);
		
			if(result.size() == 0) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INVALID_ID,
					"No such observer exists: " + 
						"ID: " + observerId + ", " + 
						"Version: " + observerVersion);
			}
			else if(result.size() > 1) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INVALID_ID,
					"Multiple observers exist: " + 
						"ID: " + observerId + ", " + 
						"Version: " + observerVersion);
			}
			
			return result.iterator().next();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Gathers all observers that match the given criteria. If all parameters
	 * are null, then all parameters visible to the user are returned.
	 * 
	 * @param id Limits the results to only those with this ID. Optional.
	 * 
	 * @param version Limits the results to only those with this version. 
	 * 				  Optional.
	 * 
	 * @param numToSkip The number of observers to skip for paging.
	 * 
	 * @param numToReturn The number of observers to return for paging.
	 * 
	 * @return The collection of observers limited by the parameters.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public Collection<Observer> getObservers(
			final String id,
			final Long version,
			final long numToSkip,
			final long numToReturn) 
			throws ServiceException {
		
		try {
			return 
				observerQueries
					.getObservers(id, version, numToSkip, numToReturn);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the stream.
	 * 
	 * @param observerId The observer's unique identifier.
	 * 
	 * @param streamId The stream's unique identifier.
	 * 
	 * @param streamVersion The stream's version.
	 * 
	 * @return The Stream or null if no stream with that observer/ID/version 
	 * 		   exists.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public Observer.Stream getStream(
			final String observerId,
			final String streamId,
			final Long streamVersion)
			throws ServiceException {
		
		if(observerId == null) {
			throw new ServiceException("The observer ID is null.");
		}
		if(streamId == null) {
			throw new ServiceException("The stream ID is null.");
		}
		if(streamVersion == null) {
			throw new ServiceException("The stream version is null.");
		}
		
		try {
			// Get all of the streams for the observers. This should have only
			// 0 or 1 elements.
			Collection<Collection<Observer.Stream>> streamCollection =
				observerQueries
					.getStreams(
						null, 
						observerId, 
						null, 
						streamId, 
						streamVersion, 
						0, 
						2).values();
			
			// If the observer doesn't exist, its stream cannot exist.
			if(streamCollection.size() == 0) {
				return null;
			}
			// No two observers should have the same ID.
			else if(streamCollection.size() > 1) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INVALID_ID,
					"Multiple observers exist with the same ID: " + 
						observerId);
			}
			
			// Get the collection of streams for the observer. This should only
			// 0 or 1 elements.
			Collection<Observer.Stream> streams =
				streamCollection.iterator().next();
			
			// If there are no elements, return null.
			if(streams.size() == 0) {
				return null;
			}
			// No two streams for the same observer should have the same
			// ID-version pair.
			else if(streams.size() > 1) {
				throw new ServiceException(
					ErrorCode.OBSERVER_INVALID_ID,
					"Multiple streams for the same observer ('" + 
						observerId + 
						"') have the same ID-version pair: " + 
						"Stream ID: " + streamId + " " +
						"Stream Version: " + streamVersion);
			}
			
			return streams.iterator().next();
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Retrieves the streams that match the given criteria. All parameters are
	 * optional.
	 * 
	 * @param username Limits the results to only those streams for which the   
	 * 				   user submitted some data.
	 * 
	 * @param observerId Limits the results to only those whose observer has 
	 * 					 this ID.
	 * 
	 * @param observerVersion Limits the results to only those whose observer
	 * 						  has this version.
	 * 
	 * @param streamId Limits the results to only those streams that have this
	 * 				   ID.
	 * 
	 * @param streamVersion Limits the results to only those streams that have
	 * 						this version.
	 * 
	 * @param numToSkip The number of streams to skip.
	 * 
	 * @param numToReturn The number of streams to return.
	 * 
	 * @return A map of observer IDs to their respective set of streams.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public Map<String, Collection<Observer.Stream>> getStreams(
			final String username,
			final String observerId,
			final Long observerVersion,
			final String streamId,
			final Long streamVersion,
			final long numToSkip,
			final long numToReturn)
			throws ServiceException {
		
		try {
			return 
				observerQueries.getStreams(
					username,
					observerId, 
					observerVersion,
					streamId, 
					streamVersion,
					numToSkip,
					numToReturn);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Validates that the uploaded data is valid by comparing it to its stream
	 * schema and creating DataStream objects.
	 * 
	 * @param observer The observer that contains the streams.
	 * 
	 * @param data The data to validate.
	 * 
	 * @param invalidPoints A list of InvalidPoint objects that dictate which
	 * 						points are not entirely valid and why.
	 * 
	 * @return A collection of DataStreams where each stream represents a 
	 * 		   different piece of data.
	 * 
	 * @throws ServiceException The data was invalid.
	 */
	public Collection<DataStream> validateData(
			final Observer observer,
			final JsonParser data,
			final List<InvalidPoint> invalidPoints)
			throws ServiceException {
		
		JsonNode nodes;
		try {
			nodes = data.readValueAsTree();
		}
		catch(JsonProcessingException e) {
			throw new ServiceException(
				ErrorCode.OBSERVER_INVALID_STREAM_DATA,
				"The data was not well-formed JSON.",
				e);
		}
		catch(IOException e) {
			throw new ServiceException(
				ErrorCode.OBSERVER_INVALID_STREAM_DATA,
				"Could not read the data from the parser.",
				e);
		}
		int numNodes = nodes.size();
		
		Collection<DataStream> result = new ArrayList<DataStream>(numNodes);
		for(int i = 0; i < numNodes; i++) {
			JsonNode node = nodes.get(i);
			
			try {
				result.add(observer.getDataStream(node));
			}
			catch(DomainException e) {
				if(invalidPoints == null) {
					throw new ServiceException(
						ErrorCode.OBSERVER_INVALID_STREAM_DATA,
						"The data was malformed: " + e.getMessage(),
						e);
				}
				else {
					LOGGER
						.warn(
							"An invalid point was detected for observer '" +
								observer.getId() +
								"' with version '" +
								observer.getVersion() +
								"': " +
								e.getMessage());
					invalidPoints
						.add(
							new InvalidPoint(
								i, 
								node.toString(), 
								e.getMessage(), 
								e));
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Prunes the duplicates from the collection of data elements. A duplicate
	 * is defined as a point with an ID whose ID already exists for the given
	 * user and for the associated stream. This will not remove duplicates in
	 * a single upload.
	 *  
	 * @param username The username of the user that will own these points.
	 * 
	 * @param observerId The observer's unique identifier.
	 * 
	 * @param data The data that has been uploaded.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void removeDuplicates(
			final String username,
			final String observerId,
			final Collection<DataStream> data)
			throws ServiceException {
		
		try {
			// Get the IDs for each stream from this upload's data.
			Map<String, Collection<String>> uploadIds = 
				new HashMap<String, Collection<String>>();
			for(DataStream dataStream : data) {
				MetaData dataStreamMetaData = dataStream.getMetaData();
				
				if(dataStreamMetaData != null) {
					String id = dataStreamMetaData.getId();
				
					if(id != null) {
						Stream stream = dataStream.getStream();
						
						Collection<String> streamIds = 
							uploadIds.get(stream.getId());
						if(streamIds == null) {
							streamIds = new LinkedList<String>();
							uploadIds.put(stream.getId(), streamIds);
						}
						streamIds.add(id);
					}
				}
			}
			
			// Get the existing IDs for each stream that are also in this 
			// upload's IDs.
			Collection<String> duplicateIds = new HashSet<String>();
			for(String streamId : uploadIds.keySet()) {
				duplicateIds.addAll( 
					observerQueries.getDuplicateIds(
						username,
						observerId,
						streamId,
						uploadIds.get(streamId)));
			}
			
			// Remove any of this upload's IDs that already exist.
			Iterator<DataStream> dataIter = data.iterator();
			while(dataIter.hasNext()) {
				DataStream dataStream = dataIter.next();
				MetaData dataStreamMetaData = dataStream.getMetaData();
				
				if(dataStreamMetaData != null) {
					String id = dataStreamMetaData.getId();
				
					if((id != null) && (duplicateIds.contains(id))) {
						dataIter.remove();
					}
				}
			}
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Stores the stream data.
	 * 
	 * @param username The user who is uploading the data.
	 * 
	 * @param observer The observer to which the data belong.
	 * 
	 * @param data The data to be stored.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void storeData(
			final String username,
			final Observer observer,
			final Collection<DataStream> data) 
			throws ServiceException {
		
		try {
			observerQueries.storeData(username, observer, data);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Stores the stream data where the points were invalid.
	 * 
	 * @param username
	 *        The username of the user to whom the points belong.
	 * 
	 * @param observer
	 *        The observer to which the points were supposed to conform.
	 * 
	 * @param invalidData
	 *        The data to be stored.
	 * 
	 * @throws ServiceException
	 *         There was an error storing the data.
	 */
	public void storeInvalidData(
		final String username,
		final Observer observer,
		final Collection<InvalidPoint> invalidData)
		throws ServiceException {
		
		try {
			observerQueries.storeInvalidData(username, observer, invalidData);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Retrieves the data for a stream.
	 * 
	 * @param stream The Stream object for the stream whose data is in 
	 * 				 question. Required.
	 * 
	 * @param username The username of the user to which the data must belong.
	 * 				   Required.
	 * 
	 * @param observerId The observer's unique identifier. Required.
	 * 
	 * @param observerVersion The observer's version. Optional.
	 * 
	 * @param startDate The earliest data point to return. Optional.
	 * 
	 * @param endDate The latest point data point to return. Optional.
	 * 
	 * @param chronological If true, the values will be sorted chronologically.
	 * 						If false, the values will be sorted reverse
	 * 						chronologically. Required.
	 * 
	 * @param numToSkip The number of data points to skip. Required.
	 * 
	 * @param numToReturn The number of data points to return. Required.
	 * 
	 * @return A list of data points in chronological order that match the 
	 * 		   query.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public List<DataStream> getStreamData(
			final Stream stream,
			final String username,
			final String observerId,
			final Long observerVersion,
			final DateTime startDate,
			final DateTime endDate,
			final boolean chronological,
			final long numToSkip,
			final long numToReturn) 
			throws ServiceException {
		
		try {
			return 
				observerQueries.readData(
					stream,
					username,
					observerId,
					observerVersion,
					startDate,
					endDate,
					chronological,
					numToSkip,
					numToReturn);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}

	/**
	 * Retrieves the invalid data for a stream.
	 * 
	 * @param observer
	 *        The observer to which the data must be associated.
	 * 
	 * @param startDate
	 *        The earliest data point to return. Optional.
	 * 
	 * @param endDate
	 *        The latest point data point to return. Optional.
	 * 
	 * @param numToSkip
	 *        The number of data points to skip. Required.
	 * 
	 * @param numToReturn
	 *        The number of data points to return. Required.
	 * 
	 * @return A collection of data points that match the query.
	 * 
	 * @throws ServiceException
	 *         There was an error.
	 */
	public Collection<InvalidPoint> getInvalidStreamData(
			final Observer observer,
			final DateTime startDate,
			final DateTime endDate,
			final long numToSkip,
			final long numToReturn) 
			throws ServiceException {
		
		try {
			return 
				observerQueries
					.readInvalidData(
						observer,
						startDate,
						endDate,
						numToSkip,
						numToReturn);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
	
	/**
	 * Updates an observer.
	 * 
	 * @param username The username of the user that is updating the observer.
	 * 
	 * @param observer The new observer.
	 * 
	 * @param unchangedStreamIds The IDs of the streams in the new observer 
	 * 							 whose version didn't change and their version.
	 * 
	 * @throws ServiceException There was an error.
	 */
	public void updateObserver(
			final String username,
			final Observer observer,
			final Map<String, Long> unchangedStreamIds)
			throws ServiceException {

		try {
			observerQueries.updateObserver(
				username,
				observer,
				unchangedStreamIds);
		}
		catch(DataAccessException e) {
			throw new ServiceException(e);
		}
	}
}