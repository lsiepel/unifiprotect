/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.unifiprotect.internal;

import static org.eclipse.smarthome.core.thing.ThingStatus.*;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.COMMUNICATION_ERROR;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingStatusInfoBuilder;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.unifiprotect.internal.model.UniFiProtectNvr;
import org.openhab.binding.unifiprotect.internal.model.UniFiProtectNvrChannel;
import org.openhab.binding.unifiprotect.internal.model.UniFiProtectStatus;
import org.openhab.binding.unifiprotect.internal.model.UniFiProtectStatus.SendStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UniFiProtectNvrThingHandler}
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
@NonNullByDefault
public class UniFiProtectNvrThingHandler extends BaseBridgeHandler implements PropertyChangeListener {

    private @Nullable ScheduledFuture<?> refreshJob;
    private final Logger logger = LoggerFactory.getLogger(UniFiProtectNvrThingHandler.class);

    private volatile boolean disposed = false;
    private @Nullable volatile UniFiProtectNvr nvr;

    private UniFiProtectNvrThingConfig config = new UniFiProtectNvrThingConfig();

    private @Nullable UniFiProtectEventManager eventManager;

    public UniFiProtectNvrThingHandler(Bridge bridge) {
        super(bridge);
    }

    @SuppressWarnings("null")
    private void cancelRefreshJob() {
        synchronized (this) {
            if (refreshJob != null) {
                logger.debug("Cancelling refresh job");
                refreshJob.cancel(true);
                refreshJob = null;
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        cancelRefreshJob();
        config = getConfig().as(UniFiProtectNvrThingConfig.class);
        logger.debug("Initializing the UniFi Protect NVR Handler with config = {}", config);
        nvr = new UniFiProtectNvr(config);
        boolean initNvr = nvr.init();
        UniFiProtectStatus status = null;
        if (initNvr) {
            status = nvr.start();
            if (status.getStatus() == SendStatus.SUCCESS) {
                eventManager = new UniFiProtectEventManager(nvr.getHttpClient(), nvr.getGson(), config);
                eventManager.start();
                eventManager.addPropertyChangeListener(this);
            }
        }
        if (initNvr && status.getStatus() == SendStatus.SUCCESS) {
            updateStatus(ONLINE);
            disposed = false;
        } else {
            final String message = initNvr ? status.getMessage() : "Failed to init nvr";
            updateStatus(OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            logger.error("UniFiProtect Offline due to failuer: {}", message, status.getException());
        }
    }

    @Override
    protected void updateStatus(ThingStatus status) {
        if (disposed) {
            cancelRefreshJob();
            eventManager.stop();
            return;
        }
        if (status == ONLINE) {
            scheduleRefreshJob();
        } else {
            logger.warn("Stopping refresh since nvr is not online");
            cancelRefreshJob();
        }
        super.updateStatus(status);
    }

    @Override
    protected void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        if (status == ONLINE || (status == OFFLINE && statusDetail == COMMUNICATION_ERROR)) {
            scheduleRefreshJob();
        } else {
            cancelRefreshJob();
        }
        ThingStatusInfo statusInfo = ThingStatusInfoBuilder.create(status, statusDetail).withDescription(description)
                .build();
        if (!statusInfo.equals(getThing().getStatusInfo())) {
            super.updateStatus(status, statusDetail, description);
        }
    }

    private void scheduleRefreshJob() {
        synchronized (this) {
            if (disposed) {
                if (refreshJob != null) {
                    refreshJob.cancel(true);
                    refreshJob = null;
                    updateStatus(OFFLINE);
                }
            }
            if (refreshJob == null && !disposed) {
                logger.debug("Scheduling refresh job every {}s", config.getRefresh());
                refreshJob = scheduler.scheduleWithFixedDelay(this::run, 0, config.getRefresh(), TimeUnit.SECONDS);
            }
        }
    }

    private void run() {
        logger.debug("Executing refresh job");
        UniFiProtectStatus status = refresh();
        if (status.getStatus() == SendStatus.SUCCESS) {
            updateStatus(ONLINE);
        } else {
            updateStatus(OFFLINE, COMMUNICATION_ERROR, status.getMessage());
        }
    }

    @Override
    public synchronized void dispose() {
        logger.debug("dispose()");
        cancelRefreshJob();
        disposed = true;
        super.dispose();
    }

    @SuppressWarnings("null")
    public synchronized UniFiProtectStatus refresh() {
        logger.debug("Refreshing Protect: {}", this.hashCode());
        UniFiProtectStatus status = UniFiProtectStatus.STATUS_NOT_SENT;

        if (nvr != null) {
            logger.debug("Refreshing the UniFi Protect Controller {}", getThing().getUID());
            status = nvr.refreshProtect();
            if (status.getStatus() == SendStatus.SUCCESS) {
                refreshNvrChannels();
                refreshCameras();
            }
        }
        return status;
    }

    public synchronized void refreshCameras() {
        getThing().getThings().forEach((thing) -> {
            if (thing.getHandler() instanceof UniFiProtectCameraThingHandler) {
                ((UniFiProtectCameraThingHandler) thing.getHandler()).refresh();
            }
        });
    }

    private synchronized void refreshNvrChannels() {
        logger.debug("Nvr Refresh!");
        if (getThing().getStatus() == ONLINE) {
            UniFiProtectNvr nvr = getNvr();
            if (nvr != null) {
                for (Channel channel : getThing().getChannels()) {
                    ChannelUID channelUID = channel.getUID();
                    refreshChannel(channelUID);
                }
            }
        }
    }

    @SuppressWarnings("null")
    private void refreshChannel(ChannelUID channelUID) {
        String channelID = channelUID.getIdWithoutGroup();
        State state = UnDefType.NULL;
        logger.debug("Refresh Channel: {}", channelID);
        UniFiProtectNvrChannel channel = UniFiProtectNvrChannel.fromString(channelID);
        if (nvr == null || nvr.getNvrDevice() == null) {
            return;
        }
        switch (channel) {
            case ENABLE_AUTOMATIC_BACKUPS:
                if (nvr.getNvrDevice().getEnableAutomaticBackups() != null) {
                    state = OnOffType.from(nvr.getNvrDevice().getEnableAutomaticBackups());
                }
                break;
            case FIRMWARE_VERSION:
                if (StringUtils.isNotBlank(nvr.getNvrDevice().getFirmwareVersion())) {
                    state = StringType.valueOf(nvr.getNvrDevice().getFirmwareVersion());
                }
                break;
            case HOST:
                if (StringUtils.isNotBlank(nvr.getNvrDevice().getHost())) {
                    state = StringType.valueOf(nvr.getNvrDevice().getHost());
                }
                break;
            case HOSTS:
                logger.debug("HOSTS: {}", nvr.getNvrDevice().getHosts());
                if (StringUtils.isNotBlank(nvr.getNvrDevice().getHosts())) {
                    state = StringType.valueOf(nvr.getNvrDevice().getHosts());
                }
                break;
            case IS_CONNECTED_TO_CLOUD:
                if (nvr.getNvrDevice().getIsConnectedToCloud() != null) {
                    state = OnOffType.from(nvr.getNvrDevice().getIsConnectedToCloud());
                }
                break;
            case LAST_SEEN:
                if (nvr.getNvrDevice().getLastSeen() != null) {
                    state = new DateTimeType(ZonedDateTime
                            .ofInstant(Instant.ofEpochMilli(nvr.getNvrDevice().getLastSeen()), ZoneId.systemDefault()));
                }
                break;
            case LAST_UPDATED_AT:
                if (nvr.getNvrDevice().getLastUpdateAt() != null) {
                    state = new DateTimeType(ZonedDateTime.ofInstant(
                            Instant.ofEpochMilli(nvr.getNvrDevice().getLastUpdateAt()), ZoneId.systemDefault()));
                }
                break;
            case NAME:
                if (StringUtils.isNotBlank(nvr.getNvrDevice().getName())) {
                    state = StringType.valueOf(nvr.getNvrDevice().getName());
                }
                break;
            case RECORDING_RETENTION_DURATION:
                if (nvr.getNvrDevice().getRecordingRetentionDurationMs() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getRecordingRetentionDurationMs());
                }
                break;
            case UPTIME:
                if (nvr.getNvrDevice().getUptime() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getUptime());
                }
                break;
            case VERSION:
                if (StringUtils.isNotBlank(nvr.getNvrDevice().getVersion())) {
                    state = StringType.valueOf(nvr.getNvrDevice().getVersion());
                }
                break;
            case HOST_SHORT_NAME:
                if (StringUtils.isNotBlank(nvr.getNvrDevice().getHostShortname())) {
                    state = StringType.valueOf(nvr.getNvrDevice().getHostShortname());
                }
                break;
            case ALERTS:
                logger.debug("Alert NVR: {}:", nvr.getNvrUser().getEnableNotifications());
                Boolean enableNotifications = nvr.getNvrUser().getEnableNotifications();
                if (enableNotifications != null) {
                    state = OnOffType.from(enableNotifications.booleanValue());
                    logger.debug("Alert NVR val: {}:", enableNotifications.booleanValue());
                }
                break;
            case CPU_AVERAGE_LOAD:
                if (nvr.getNvrDevice().getCpuAverageLoad() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getCpuAverageLoad());
                }
                break;
            case CPU_TEMPERATURE:
                if (nvr.getNvrDevice().getCpuTemperature() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getCpuTemperature());
                }
                break;
            case DEVICE_0_HEALTHY:
                if (nvr.getNvrDevice().getDevice0Healthy() != null) {
                    state = OnOffType.from(nvr.getNvrDevice().getDevice0Healthy());
                }
                break;
            case DEVICE_0_MODEL:
                if (nvr.getNvrDevice().getDevice0Model() != null) {
                    state = StringType.valueOf(nvr.getNvrDevice().getDevice0Model());
                }
                break;
            case DEVICE_0_SIZE:
                if (nvr.getNvrDevice().getDevice0Size() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getDevice0Size());
                }
                break;
            case MEM_AVAILABLE:
                if (nvr.getNvrDevice().getMemAvailable() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getMemAvailable());
                }
                break;
            case MEM_FREE:
                if (nvr.getNvrDevice().getMemFree() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getMemFree());
                }
                break;
            case MEM_TOTAL:
                if (nvr.getNvrDevice().getMemTotal() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getMemTotal());
                }
                break;
            case STORAGE_AVAILABLE:
                if (nvr.getNvrDevice().getStorageAvailable() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getStorageAvailable());
                }
                break;
            case STORAGE_TOTAL_SIZE:
                if (nvr.getNvrDevice().getStorageSize() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getStorageSize());
                }
                break;
            case STORAGE_TYPE:
                String type = nvr.getNvrDevice().getStorageType();
                if (type != null && StringUtils.isNotBlank(type)) {
                    state = StringType.valueOf(nvr.getNvrDevice().getSystemInfo().getStorage().getType());
                }
                break;
            case STORAGE_USED:
                if (nvr.getNvrDevice().getStorageUsed() != null) {
                    state = new DecimalType(nvr.getNvrDevice().getStorageUsed());
                }
                break;
            default:
                break;
        }
        if (state != UnDefType.NULL) {
            updateState(channelID, state);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channelId = channelUID.getIdWithoutGroup();
        UniFiProtectNvrChannel channel = UniFiProtectNvrChannel.fromString(channelId);
        UniFiProtectNvr nvr = getNvr();
        if (nvr == null) {
            logger.debug("Failed to handle command since there is no nvr");
            return;
        }
        switch (channel) {
            case ALERTS:
                handleAlerts(nvr, channelUID, command);
                break;
            default:
                break;

        }
    }

    @SuppressWarnings("null")
    private void handleAlerts(UniFiProtectNvr nvr, ChannelUID channelUID, Command command) {
        synchronized (this) {
            if (!(command instanceof OnOffType)) {
                logger.debug("Ignoring unsupported command = {} for channel = {} - valid commands types are: OnOffType",
                        command, channelUID);
                return;
            }

            logger.info("Turning alerts: {}  camera: {}, ip: {}", command == OnOffType.ON, nvr.getNvrDevice().getName(),
                    nvr.getNvrDevice().getHost());
            getNvr().turnOnOrOffAlerts(command == OnOffType.ON);
        }
    }

    public @Nullable UniFiProtectNvr getNvr() {
        return nvr;
    }

    public static boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return UniFiProtectBindingConstants.THING_TYPE_NVR.equals(thingTypeUID);
    }

    @Override
    public void propertyChange(@Nullable PropertyChangeEvent evt) {
        if (evt.getPropertyName() == UniFiProtectEventManager.EVENT_MOTION) {
            getNvr().refreshProtect();
            getNvr().refreshEvents();
            refreshCameras();
        }
    }

}
