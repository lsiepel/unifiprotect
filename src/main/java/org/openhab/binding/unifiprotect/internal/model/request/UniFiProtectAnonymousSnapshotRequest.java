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
package org.openhab.binding.unifiprotect.internal.model.request;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpScheme;
import org.openhab.binding.unifiprotect.internal.UniFiProtectNvrThingConfig;
import org.openhab.binding.unifiprotect.internal.model.UniFiProtectNvrType;
import org.openhab.binding.unifiprotect.internal.types.UniFiProtectCamera;

/**
 * The {@link UniFiProtectAnonymousSnapshotRequest}
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
@NonNullByDefault
public class UniFiProtectAnonymousSnapshotRequest extends UniFiProtectRequest {

    private static final String API_SNAP_JPEG = "/snap.jpeg";
    private static final int PORT_80 = 80;

    public UniFiProtectAnonymousSnapshotRequest(HttpClient httpClient, UniFiProtectCamera camera,
            UniFiProtectNvrType nvrType, UniFiProtectNvrThingConfig config) {
        super(httpClient, config);
        String host = camera.getHost();
        setHost(host == null ? "" : host);
        setPort(PORT_80);
        setPath(API_SNAP_JPEG);
    }

    @Override
    protected String getHttpScheme() {
        return HttpScheme.HTTP.asString();
    }
}