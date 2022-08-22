/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.lifecycle;

import androidx.annotation.VisibleForTesting;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.Lifecycle;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.ServiceProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * LifecycleExtension class
 *
 * The responsibility of LifecycleExtension is to handle the calculation and population of a base set of data within
 * the SDK. This data will consist of information about the lifecycle of the app involving launches, installs
 * and upgrades.
 *
 * This extension handles two main scenarios:
 * <ul>
 * 		<li> Computing standard lifecycle sessions, usually consumed by the Analytics extension</li>
 *  	<li> Computing the application launch/close XDM metrics, usually consumed by the Edge Network and related extensions</li>
 * </ul>
 */
public class LifecycleExtension extends Extension {

	private static final String SELF_LOG_TAG                = "LifecycleExtension";
	private final NamedCollection lifecycleDataStore;
	private final DeviceInforming deviceInfoService;
	private final LifecycleState lifecycleState;
	private final LifecycleV2Extension lifecycleV2;

	/**
	 * Constructor for the LifecycleExtension, must be called by inheritors.
	 * It is called by the Mobile SDK when registering the extension and it initializes the extension and registers event listeners.
	 *
	 * @param extensionApi {@code ExtensionApi} instance
	 */
	protected LifecycleExtension(final ExtensionApi extensionApi) {
		super(extensionApi);
		lifecycleDataStore = getDataStore();
		deviceInfoService = getDeviceInfoService();
		lifecycleState = new LifecycleState(lifecycleDataStore, deviceInfoService);
		lifecycleV2 = new LifecycleV2Extension(lifecycleDataStore, deviceInfoService, getApi());
	}

	/**
	 * This constructor is intended for testing purposes.
	 *
	 * @param extensionApi {@code ExtensionApi} instance
	 * @param namedCollection {@code NamedCollection} instance
	 * @param deviceInfoService {@code DeviceInforming} instance
	 */
	@VisibleForTesting
	protected LifecycleExtension(final ExtensionApi extensionApi,
								 final NamedCollection namedCollection,
								 final DeviceInforming deviceInfoService) {
		super(extensionApi);
		lifecycleDataStore = namedCollection;
		this.deviceInfoService = deviceInfoService;
		lifecycleState = new LifecycleState(lifecycleDataStore, deviceInfoService);
		lifecycleV2 = new LifecycleV2Extension(lifecycleDataStore, deviceInfoService, getApi());
	}

	/**
	 * This constructor is intended for testing purposes.
	 *
	 * @param extensionApi {@code ExtensionApi} instance
	 * @param namedCollection {@code NamedCollection} instance
	 * @param deviceInfoService {@code DeviceInforming} instance
	 * @param lifecycleState {@code LifecycleState} instance
	 * @param lifecycleV2Extension {@code LifecycleV2Extension} instance
	 */
	@VisibleForTesting
	protected LifecycleExtension(final ExtensionApi extensionApi,
								 final NamedCollection namedCollection,
								 final DeviceInforming deviceInfoService,
								 final LifecycleState lifecycleState,
								 final LifecycleV2Extension lifecycleV2Extension) {
		super(extensionApi);
		lifecycleDataStore = namedCollection;
		this.deviceInfoService = deviceInfoService;
		this.lifecycleState = lifecycleState;
		lifecycleV2 = lifecycleV2Extension;
	}

	@Override
	protected String getName() {
		return LifecycleConstants.EventDataKeys.Lifecycle.MODULE_NAME;
	}

	@Override
	protected String getVersion() {
		return Lifecycle.extensionVersion();
	}

	@Override
	protected String getFriendlyName() {
		return LifecycleConstants.FRIENDLY_NAME;
	}

	@Override
	protected void onRegistered() {
		getApi().registerEventListener(EventType.GENERIC_LIFECYCLE, EventSource.REQUEST_CONTENT, this::handleLifecycleRequestEvent);
		getApi().registerEventListener(EventType.HUB, EventSource.BOOTED, this::handleEventHubBootEvent);
		getApi().registerEventListener(EventType.WILDCARD, EventSource.WILDCARD, this::updateLastKnownTimestamp);
	}

	@Override
	public boolean readyForEvent(Event event) {
		if (event.getType().equalsIgnoreCase(EventType.GENERIC_LIFECYCLE) && event.getSource().equalsIgnoreCase(EventSource.REQUEST_CONTENT)) {
			SharedStateResult configurationSharedState = getApi().getSharedState(LifecycleConstants.EventDataKeys.Configuration.MODULE_NAME, event, false, SharedStateResolution.ANY);
			if (configurationSharedState != null) {
				return configurationSharedState.status == SharedStateStatus.SET;
			} else {
				return false;
			}
		}
		return true;
	}

	/**
	 * Processes an event of type generic lifecycle and source request content
	 * @param event lifecycle request content {@code Event}
	 */
	void handleLifecycleRequestEvent(final Event event) {
		if (event == null) {
			Log.trace(SELF_LOG_TAG, "Failed to process request content event, event is null");
			return;
		}

		SharedStateResult configurationSharedState = getApi().getSharedState(LifecycleConstants.EventDataKeys.Configuration.MODULE_NAME, event, false, SharedStateResolution.ANY);

		if (configurationSharedState == null ||  configurationSharedState.status == SharedStateStatus.PENDING) {
			Log.trace(SELF_LOG_TAG, "Configuration is pending, lifecycle request event is not processed");
			return;
		}

		Map<String, Object> eventData = event.getEventData();

		if (eventData == null) {
			Log.trace(SELF_LOG_TAG, "Failed to process lifecycle event, event data is null");
			return;
		}

		String lifecycleAction = (String) eventData.get(LifecycleConstants.EventDataKeys.Lifecycle.LIFECYCLE_ACTION_KEY);

		if (LifecycleConstants.EventDataKeys.Lifecycle.LIFECYCLE_START.equals(lifecycleAction)) {
			Log.debug(SELF_LOG_TAG, "Starting lifecycle");
			startApplicationLifecycle(event, configurationSharedState.value);
		} else if (LifecycleConstants.EventDataKeys.Lifecycle.LIFECYCLE_PAUSE.equals(lifecycleAction)) {
			Log.debug(SELF_LOG_TAG, "Pausing lifecycle");
			pauseApplicationLifecycle(event);
		} else {
			Log.trace(SELF_LOG_TAG, "Failed to process lifecycle request content event, invalid action");
		}
	}

	/**
	 * Updates the lifecycle shared state with current context data and default data when a boot event is received
	 *
	 * @param event to be processed
	 */
	void handleEventHubBootEvent(final Event event) {
		updateLifecycleSharedState(event,
				0,
				lifecycleState.computeBootData(event.getTimestampInSeconds())
		);
	}

	/**
	 * Updates the last known event timestamp in cache and if needed in persistence
	 *
	 * @param event to be processed; should not be null
	 */
	void updateLastKnownTimestamp(final Event event) {
		lifecycleV2.updateLastKnownTimestamp(event);
	}

	/**
	 * Gets advertising identifier.
	 *
	 * @param event Event containing advertising identifier data
	 *
	 * @return the advertising identifier
	 */
	private String getAdvertisingIdentifier(final Event event) {
		if (event == null) {
			Log.trace(SELF_LOG_TAG, "Failed to get advertising identifier, event is null");
			return null;
		}

		SharedStateResult identitySharedState = getApi().getSharedState(LifecycleConstants.EventDataKeys.Identity.MODULE_NAME, event, false, SharedStateResolution.ANY);

		if (identitySharedState != null && identitySharedState.status == SharedStateStatus.PENDING) {
			return null;
		}

		if (identitySharedState != null && identitySharedState.value != null) {
			try {
				return (String) identitySharedState.value.get(LifecycleConstants.EventDataKeys.Identity.ADVERTISING_IDENTIFIER);
			} catch (Exception e) {
				return null;
			}
		}

		return null;
	}

	/**
	 * Start the lifecycle session for standard and XDM workflows
	 *
	 * @param event current lifecycle event to be processed
	 * @param configurationSharedState configuration shared state data for this event
	 */
	private void startApplicationLifecycle(final Event event, final Map<String, Object> configurationSharedState) {
		boolean isInstall = isInstall();

		final long startTimestampInSeconds = event.getTimestampInSeconds();

		Map<String, Object> eventData = event.getEventData();
		Map<String, String> additionalContextData = null;

		if (eventData != null) {
			try {
				additionalContextData = (Map<String, String>) eventData.get(LifecycleConstants.EventDataKeys.Lifecycle.ADDITIONAL_CONTEXT_DATA);
			} catch (Exception e) {
				Log.trace(SELF_LOG_TAG, "Request content event data error, event data is null");
			}
		}

		LifecycleSession.SessionInfo previousSessionInfo = lifecycleState.start(startTimestampInSeconds,
				additionalContextData,
				getAdvertisingIdentifier(event),
				getSessionTimeoutLength(configurationSharedState),
				isInstall);

		if (previousSessionInfo == null) {
			// Analytics extension needs adjusted start date to calculate timeSinceLaunch param.
			if (lifecycleDataStore != null) {
				final long startTime = lifecycleDataStore.getLong(LifecycleConstants.DataStoreKeys.START_DATE, 0L);
				updateLifecycleSharedState(event, startTime, lifecycleState.getContextData());
				return;
			}
		}

		updateLifecycleSharedState(event, startTimestampInSeconds, lifecycleState.getContextData());
		if (previousSessionInfo != null) {
			dispatchSessionStart(startTimestampInSeconds, previousSessionInfo.getStartTimestampInSeconds(), previousSessionInfo.getPauseTimestampInSeconds());
		}

		lifecycleV2.start(event, isInstall);

		if (isInstall) {
			persistInstallDate(event);
		}
	}

	/**
	 * Pause the lifecycle session for standard and XDM workflows
	 *
	 * @param event current lifecycle event to be processed
	 */
	private void pauseApplicationLifecycle(final Event event) {
		lifecycleState.pause(event);
		lifecycleV2.pause(event);
	}

	/**
	 * Persist Application install date.
	 *
	 * @param event lifecycle start event.
	 */
	private void persistInstallDate(final Event event) {
		if (lifecycleDataStore == null) {
			return;
		}

		final long startTimestampInSeconds = event.getTimestampInSeconds();
		lifecycleDataStore.setLong(LifecycleConstants.DataStoreKeys.INSTALL_DATE, startTimestampInSeconds);
	}

	/**
	 * Check if install has been processed
	 *
	 * @return true if there is no install date stored in the data store
	 */
	private boolean isInstall() {
		return lifecycleDataStore != null && !lifecycleDataStore.contains(LifecycleConstants.DataStoreKeys.INSTALL_DATE);
	}

	/**
	 * Reads the session timeout from the configuration shared state, if not found returns the default session timeout
	 * @param configurationSharedState current configuration shared state
	 * @return session timeout
	 */
	private long getSessionTimeoutLength(Map<String, Object> configurationSharedState) {
		long sessionTimeoutInSeconds = LifecycleConstants.DEFAULT_LIFECYCLE_TIMEOUT;
		if (configurationSharedState != null) {
			Object sessionTimeout = configurationSharedState.get(LifecycleConstants.EventDataKeys.Configuration.LIFECYCLE_CONFIG_SESSION_TIMEOUT);
			if(sessionTimeout != null) {
				try {
					sessionTimeoutInSeconds = (long) sessionTimeout;
				} catch (Exception e) {
					return sessionTimeoutInSeconds;
				}
			}
		}
		return sessionTimeoutInSeconds;
	}

	/**
	 * Fetches the {@link DeviceInforming} from {@link ServiceProvider}
	 *
	 * @return {@code DeviceInforming} or null if something went wrong
	 */
	private DeviceInforming getDeviceInfoService() {
		return ServiceProvider.getInstance().getDeviceInfoService();
	}

	/**
	 * Fetches the {@link NamedCollection} for LifecycleExtension from {@link ServiceProvider}
	 *
	 * @return {@code NamedCollection} for LifecycleExtension or null if something went wrong
	 */
	private NamedCollection getDataStore() {
		return ServiceProvider.getInstance().getDataStoreService().getNamedCollection(LifecycleConstants.DATA_STORE_NAME);
	}

	/**
	 * Updates lifecycle shared state versioned at {@code event} with {@code contextData}
	 *
	 * @param event the event to version the shared state at
	 * @param startTimestampInSeconds  The current session start timestamp in seconds
	 * @param contextData {@code Map<String, String>} context data to be updated
	 */
	private void updateLifecycleSharedState(final Event event,
											final long startTimestampInSeconds,
											final Map<String, String> contextData) {
		Map<String, Object> lifecycleSharedState = new HashMap<>();
		lifecycleSharedState.put(LifecycleConstants.EventDataKeys.Lifecycle.SESSION_START_TIMESTAMP,
									 startTimestampInSeconds);
		lifecycleSharedState.put(LifecycleConstants.EventDataKeys.Lifecycle.MAX_SESSION_LENGTH,
									 LifecycleConstants.MAX_SESSION_LENGTH_SECONDS);
		lifecycleSharedState.put(LifecycleConstants.EventDataKeys.Lifecycle.LIFECYCLE_CONTEXT_DATA, contextData);
		getApi().createSharedState(lifecycleSharedState, event);
	}

	/**
	 * Dispatches a Lifecycle response content event with appropriate event data
	 * @param startTimestampInSeconds session start time
	 * @param previousStartTime start time of previous session
	 * @param previousPauseTime pause time of previous session
	 */
	private void dispatchSessionStart(long startTimestampInSeconds, long previousStartTime, long previousPauseTime){
		// Dispatch a new event with session related data
		Map<String, Object> eventDataMap = new HashMap<>();
		eventDataMap.put(LifecycleConstants.EventDataKeys.Lifecycle.LIFECYCLE_CONTEXT_DATA, lifecycleState.getContextData());
		eventDataMap.put(LifecycleConstants.EventDataKeys.Lifecycle.SESSION_EVENT,
				LifecycleConstants.EventDataKeys.Lifecycle.LIFECYCLE_START);
		eventDataMap.put(LifecycleConstants.EventDataKeys.Lifecycle.SESSION_START_TIMESTAMP, startTimestampInSeconds);
		eventDataMap.put(LifecycleConstants.EventDataKeys.Lifecycle.MAX_SESSION_LENGTH,
				LifecycleConstants.MAX_SESSION_LENGTH_SECONDS);
		eventDataMap.put(LifecycleConstants.EventDataKeys.Lifecycle.PREVIOUS_SESSION_START_TIMESTAMP, previousStartTime);
		eventDataMap.put(LifecycleConstants.EventDataKeys.Lifecycle.PREVIOUS_SESSION_PAUSE_TIMESTAMP, previousPauseTime);

		final Event startEvent = new Event.Builder(
				LifecycleConstants.EventName.LIFECYCLE_START_EVENT,
				EventType.LIFECYCLE,
				EventSource.RESPONSE_CONTENT).setEventData(eventDataMap).build();

		getApi().dispatch(startEvent);
	}
}
