/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.tools.jlink.internal;

import jdk.tools.jlink.plugin.ResourcePoolModule;

import java.util.Locale;

/**
 * Supported platforms
 */
public enum Platform {
    WINDOWS,
    LINUX,
    SOLARIS,
    MACOS,
    AIX,
    UNKNOWN;

    /**
     * Returns the {@code Platform} of the given OS name specified
     * in the {@code ModuleTarget} attribute.
     *
     * @param osName OS name in ModuleTarget attribute
     */
    public static Platform toPlatform(String osName) {
        try {
            return Platform.valueOf(osName.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            return Platform.UNKNOWN;
        }
    }

    /**
     * Returns the {@code Platform} to which the given module is target to.
     */
    public static Platform getTargetPlatform(ResourcePoolModule module) {
        String osName = module.osName();
        if (osName != null) {
            return toPlatform(osName);
        } else {
            return Platform.UNKNOWN;
        }
    }
}
