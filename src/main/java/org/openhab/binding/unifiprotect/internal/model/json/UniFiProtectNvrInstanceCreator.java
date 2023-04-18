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
package org.openhab.binding.unifiprotect.internal.model.json;

import java.lang.reflect.Type;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.types.UniFiProtectNvrDevice;

import com.google.gson.InstanceCreator;

/**
 * The {@link UniFiProtectNvrInstanceCreator}
 *
 * @author Joseph (Seaside) Hagberg - Initial contribution
 */
public class UniFiProtectNvrInstanceCreator implements InstanceCreator<UniFiProtectNvrDevice> {

    @Override
    public UniFiProtectNvrDevice createInstance(@Nullable Type type) {
        if (type == null) {
            return null;
        }

        return UniFiProtectNvrDevice.class.equals(type) ? new UniFiProtectNvrDevice() : null;
    }
}
