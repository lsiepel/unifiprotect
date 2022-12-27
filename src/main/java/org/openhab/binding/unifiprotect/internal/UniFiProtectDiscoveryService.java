/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.model.UniFiProtectNvr;
import org.openhab.binding.unifiprotect.internal.thing.UniFiProtectBridgeHandler;
import org.openhab.binding.unifiprotect.internal.types.UniFiProtectCamera;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UniFiProtectDiscoveryService}
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
@NonNullByDefault
public class UniFiProtectDiscoveryService extends AbstractDiscoveryService {

    private static final String UNIFI_PROTECT_VENDOR = "Ubiquiti UniFi Protect";
    private static final String UNIFI_PROTECT = "UniFi Protect: ";

    private static final int TIMEOUT = 30;
    private static final int REFRESH_SECONDS = 1200;
    private UniFiProtectBridgeHandler bridgeHandler;
    private @Nullable ScheduledFuture<?> discoveryFuture;

    private final Logger logger = LoggerFactory.getLogger(UniFiProtectDiscoveryService.class);

    public UniFiProtectDiscoveryService(UniFiProtectBridgeHandler bridge) throws IllegalArgumentException {
        super(UniFiProtectBindingConstants.SUPPORTED_DEVICE_THING_TYPES_UIDS, TIMEOUT);
        this.bridgeHandler = bridge;
        new UniFiProtectScan();
        logger.debug("Initializing UniFiProtect Discovery Nvr: {}", bridge);
        activate(null);
    }

    @Override
    protected void startScan() {
        if (!bridgeHandler.getThing().getStatus().equals(ThingStatus.ONLINE)) {
            logger.debug("Bridge is OFFLINE, can't scan for devices!");
            return;
        }
        UniFiProtectNvr localNVR = bridgeHandler.getNvr();
        if (localNVR == null) {
            logger.debug("Failed to start discovery scan due to no nvr exists in the bridge");
            return;
        }

        logger.debug("Starting scan of UniFiProtect Server {}", bridgeHandler.getThing().getUID());

        localNVR.getCameraInsightCache().getCameras().forEach(camera -> logger.debug("Found Camera: {}", camera));

        for (Thing thing : bridgeHandler.getThing().getThings()) {
            if (thing instanceof UniFiProtectCamera) {
                logger.debug("Found existing camera already!");
            }
        }
        ThingUID bridgeUid = bridgeHandler.getThing().getUID();
        for (UniFiProtectCamera camera : localNVR.getCameraInsightCache().getCameras()) {
            ThingUID thingUID = new ThingUID(getThingType(camera), bridgeUid, camera.getMac());

            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID)
                    .withLabel(UNIFI_PROTECT.concat(camera.getName())).withBridge(bridgeUid)
                    .withProperty(Thing.PROPERTY_VENDOR, UNIFI_PROTECT_VENDOR)
                    .withProperty(UniFiProtectBindingConstants.CAMERA_PROP_HOST, camera.getHost())
                    .withProperty(UniFiProtectBindingConstants.CAMERA_PROP_MAC, camera.getMac())
                    .withProperty(UniFiProtectBindingConstants.CAMERA_PROP_NAME, camera.getName()).build();

            thingDiscovered(discoveryResult);
        }
    }

    private ThingTypeUID getThingType(UniFiProtectCamera camera) {
        final String type = camera.getType();
        if (type != null) {
            if (type.startsWith(UniFiProtectBindingConstants.G4_DOORBELL)) {
                return UniFiProtectBindingConstants.THING_TYPE_G4_DOORBELL;
            }

            if (type.startsWith(UniFiProtectBindingConstants.G4_CAMERA_PREFIX)) {
                return UniFiProtectBindingConstants.THING_TYPE_G4_CAMERA;
            }

            if (type.startsWith(UniFiProtectBindingConstants.G3_CAMERA_PREFIX)) {
                return UniFiProtectBindingConstants.THING_TYPE_G3_CAMERA;
            }
        }
        logger.error("Faild to identify UnifiProtect camera, assuming: {} actual type: {}",
                UniFiProtectBindingConstants.THING_TYPE_G3_CAMERA, type);
        return UniFiProtectBindingConstants.THING_TYPE_G3_CAMERA;
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }

    @Override
    protected void startBackgroundDiscovery() {
        logger.debug("Start UniFi Protect background discovery");

        ScheduledFuture<?> localDiscoveryFuture = discoveryFuture;
        if (localDiscoveryFuture == null || localDiscoveryFuture.isCancelled()) {
            discoveryFuture = scheduler.scheduleWithFixedDelay(this::startScan, 30, REFRESH_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        logger.debug("Stopping UniFi Protect background discovery");

        ScheduledFuture<?> localDiscoveryFuture = discoveryFuture;
        if (localDiscoveryFuture != null) {
            if (!localDiscoveryFuture.isCancelled()) {
                localDiscoveryFuture.cancel(true);
                localDiscoveryFuture = null;
            }
        }
    }

    public class UniFiProtectScan implements Runnable {

        @Override
        public void run() {
            startScan();
        }
    }
}
