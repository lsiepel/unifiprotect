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
package org.openhab.binding.unifiprotect.internal.model;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.model.request.UniFiProtectRequest;

/**
 * The {@link UniFiProtectRequest}
 *
 * @author Joseph Seaside Hagberg - Initial contribution
 *
 */
@NonNullByDefault
public class UniFiProtectStatus {

    public static final UniFiProtectStatus STATUS_NOT_SENT = new UniFiProtectStatus(SendStatus.NOT_SENT);
    private static final String MSG_EXECUTION_FAULT = "Execution fault when sending request to UniFi Protect";
    private static final String MSG_INTERRUPTED = "Interrupted while transmitting request to UniFi Protect";
    private static final String MSG_SUCCESS = "Sucessfully sent request to UniFi Protect";
    private static final String MSG_TIMEOUT = "Timeout while sending request to UniFi Protect";
    private static final String MSG_UNHANDLED_CASE = "Unhandled fault while sending request to UniFi Protect";
    private static final String MSG_NOT_SENT = "Request not sent";
    private final SendStatus status;
    private final @Nullable Exception exception;

    public UniFiProtectStatus(SendStatus status) {
        this.status = status;
        this.exception = null;
    }

    public UniFiProtectStatus(SendStatus status, Exception exception) {
        this.status = status;
        this.exception = exception;
    }

    public SendStatus getStatus() {
        return status;
    }

    public @Nullable Exception getException() {
        return exception;
    }

    public enum SendStatus {
        SUCCESS,
        TIMEOUT,
        INTERRUPTED,
        NOT_SENT,
        EXECUTION_FAULT;
    }

    public String getMessage() {
        switch (status) {
            case EXECUTION_FAULT:
                return MSG_EXECUTION_FAULT;
            case INTERRUPTED:
                return MSG_INTERRUPTED;
            case SUCCESS:
                return MSG_SUCCESS;
            case NOT_SENT:
                return MSG_NOT_SENT;
            case TIMEOUT:
                return MSG_TIMEOUT;
            default:
                return MSG_UNHANDLED_CASE;
        }

    }
}