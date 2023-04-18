/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.unifiprotect.websocket;

/**
 * The {@link UniFiProtectAction}
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
public class UniFiProtectAction {
    public static final String MODEL_KEY_EVENT = "event";

    public static final String ACTION_ADD = "add";
    public static final String ACTION_UPDATE = "update";
    public static final String PROPERTY_EVENT_ACTION_UPDATE = "EVENT_ACTION_UPDATE";
    public static final String PROPERTY_EVENT_ACTION_ADD = "EVENT_ACTION_ADD";
    public static final String PROPERTY_SOCKET_CLOSED = "SOCKET_CLOSED";
    public static final String PROPERTY_SOCKET_CONNECTED = "SOCKET_CONNECTED";
    public static final String MODEL_KEY_CAMERA = "camera";

    private String action;
    private String modelKey;
    private String id;
    private String newUpdateId;
    private String mac;

    @Override
    public String toString() {
        return "UniFiProtectAction [action=" + action + ", modelKey=" + modelKey + ", id=" + id + "]";
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getModelKey() {
        return modelKey;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public String getId() {
        final int dash = id.indexOf('-');
        if (dash > 0) {
            id = id.substring(0, dash);
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNewUpdateId() {
        return newUpdateId;
    }

    public void setNewUpdateId(String newUpdateId) {
        this.newUpdateId = newUpdateId;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }
}
