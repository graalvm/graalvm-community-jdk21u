/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.Arrays;

import javax.management.ObjectName;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.heap.AbstractMXBean;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.util.UnsignedUtils;

import sun.management.Util;

public abstract class AbstractMemoryPoolMXBean extends AbstractMXBean implements MemoryPoolMXBean {

    protected static final UnsignedWord UNDEFINED = WordFactory.signed(UNDEFINED_MEMORY_USAGE);
    private static final UnsignedWord UNINITIALIZED = WordFactory.zero();

    private final String name;
    private final String[] managerNames;
    protected final UninterruptibleUtils.AtomicUnsigned peakUsage = new UninterruptibleUtils.AtomicUnsigned();

    protected UnsignedWord initialValue = UNINITIALIZED;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected AbstractMemoryPoolMXBean(String name, String... managerNames) {
        this.name = name;
        this.managerNames = managerNames;
    }

    UnsignedWord getInitialValue() {
        if (initialValue.equal(UNINITIALIZED)) {
            initialValue = computeInitialValue();
        }
        return initialValue;
    }

    abstract UnsignedWord computeInitialValue();

    abstract UnsignedWord getMaximumValue();

    abstract void beforeCollection();

    abstract void afterCollection();

    MemoryUsage memoryUsage(UnsignedWord usedAndCommitted) {
        return memoryUsage(usedAndCommitted, usedAndCommitted);
    }

    MemoryUsage memoryUsage(UnsignedWord used, UnsignedWord committed) {
        /*
         * Actual memory usage may temporarily exceed the maximum. It would be better to return
         * UNDEFINED as the maximum value but this could break compatibility (i.e., we only do that
         * starting with GraalVM 24.0).
         */
        long max = UnsignedUtils.max(getMaximumValue(), UnsignedUtils.max(used, committed)).rawValue();
        return new MemoryUsage(getInitialValue().rawValue(), used.rawValue(), committed.rawValue(), max);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String[] getMemoryManagerNames() {
        return Arrays.copyOf(managerNames, managerNames.length);
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",name=" + name);
    }

    @Override
    public MemoryType getType() {
        return MemoryType.HEAP;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public boolean isUsageThresholdSupported() {
        return false;
    }

    @Override
    public long getUsageThreshold() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public void setUsageThreshold(long l) {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public boolean isUsageThresholdExceeded() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public long getUsageThresholdCount() {
        throw new UnsupportedOperationException("Usage threshold is not supported");
    }

    @Override
    public boolean isCollectionUsageThresholdSupported() {
        return false;
    }

    @Override
    public long getCollectionUsageThreshold() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public void setCollectionUsageThreshold(long l) {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public boolean isCollectionUsageThresholdExceeded() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public long getCollectionUsageThresholdCount() {
        throw new UnsupportedOperationException("Collection usage threshold is not supported");
    }

    @Override
    public void resetPeakUsage() {
        peakUsage.set(WordFactory.zero());
    }

    void updatePeakUsage(UnsignedWord value) {
        UnsignedWord current;
        do {
            current = peakUsage.get();
        } while (value.aboveThan(current) && !peakUsage.compareAndSet(current, value));
    }
}
