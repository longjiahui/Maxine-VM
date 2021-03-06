/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.sun.max.vm.monitor.modal.schemes.biased_inflated;

import com.sun.max.annotate.*;
import com.sun.max.vm.monitor.modal.modehandlers.*;
import com.sun.max.vm.monitor.modal.modehandlers.inflated.*;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.biased.*;
import com.sun.max.vm.monitor.modal.schemes.*;

/**
 * A modal monitor scheme that transitions between biased locks and inflated monitors.
 */
public class BiasedInflatedMonitorScheme extends ModalMonitorScheme {
    @HOSTED_ONLY
    public BiasedInflatedMonitorScheme() {
        super(BiasedLockModeHandler.asFastPath(false,
                               InflatedMonitorModeHandler.asBiasedLockDelegate()));
    }

    @Override
    public ModalLockwordDecoder getModalLockwordDecoder() {
        return new ModalLockwordDecoder() {
            public boolean isLockwordInMode(ModalLockword modalLockword, Class<? extends ModalLockword> mode) {
                if (mode == BiasedLockword.class) {
                    return BiasedLockword.isBiasedLockword(modalLockword);
                } else if (mode == InflatedMonitorLockword.class) {
                    return InflatedMonitorLockword.isInflatedMonitorLockword(modalLockword);
                }
                return false;
            }

        };
    }
}
