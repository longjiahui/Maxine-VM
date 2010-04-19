/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.ins.gui;

import com.sun.max.ins.*;
import com.sun.max.tele.*;

/**
 * A label that displays the name of a known memory region and acts as a drag source.
 *
 * @author Michael Van De Vanter
 */
public final class MemoryRegionNameLabel extends AbstractMemoryRegionLabel implements Prober {

    /**
     * Creates a label that displays the name of a known memory region and
     * acts as a drag source.
     *
     * @param inspection
     * @param memoryRegion a memory region in the VM
     * @return a component for displaying the cell
     */
    public MemoryRegionNameLabel(Inspection inspection, MaxMemoryRegion memoryRegion) {
        super(inspection, memoryRegion);
        redisplay();
        refresh(true);
    }

    public void redisplay() {
        setFont(style().javaNameFont());
    }

    public void refresh(boolean force) {
        final String regionName = memoryRegion.regionName();
        setText(regionName);
        setToolTipText("Memory region: " + regionName);
    }
}
