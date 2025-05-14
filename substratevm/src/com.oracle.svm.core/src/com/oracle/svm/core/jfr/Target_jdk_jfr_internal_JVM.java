/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import static com.oracle.svm.core.jfr.Target_jdk_jfr_internal_JVM_Util.jfrNotSupportedException;

import java.util.List;

import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.svm.core.Containers;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jfr.traceid.JfrTraceId;

import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogTag;

/**
 * The substitutions below are always active, even if the JFR support is disabled. Otherwise, we
 * would see an {@link UnsatisfiedLinkError} if a JFR native method is called at run-time.
 */
@SuppressWarnings({"static-method", "unused"})
@TargetClass(value = jdk.jfr.internal.JVM.class)
public final class Target_jdk_jfr_internal_JVM {
    // Checkstyle: stop
    @Alias //
    static Object CHUNK_ROTATION_MONITOR;
    // Checkstyle: resume

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private volatile boolean nativeOK;

    /** See {@link JVM#registerNatives}. */
    @Substitute
    private static void registerNatives() {
    }

    /** See {@link JVM#markChunkFinal}. */
    @Substitute
    public void markChunkFinal() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().markChunkFinal();
    }

    /** See {@link JVM#beginRecording}. */
    @Substitute
    public void beginRecording() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().beginRecording();
    }

    /** See {@link JVM#isRecording}. */
    @Substitute
    @Uninterruptible(reason = "Needed for calling SubstrateJVM.isRecording().")
    public boolean isRecording() {
        if (!HasJfrSupport.get()) {
            return false;
        }
        return SubstrateJVM.get().isRecording();
    }

    /** See {@link JVM#endRecording}. */
    @Substitute
    public void endRecording() {
        if (!HasJfrSupport.get()) {
            /* Nothing to do. */
            return;
        }
        SubstrateJVM.get().endRecording();
    }

    /** See {@link JVM#counterTime}. */
    @Substitute
    public static long counterTime() {
        return JfrTicks.elapsedTicks();
    }

    /** See {@link JVM#emitEvent}. */
    @Substitute
    public boolean emitEvent(long eventTypeId, long timestamp, long when) {
        return false;
    }

    /** See {@link JVM#getAllEventClasses}. */
    @Substitute
    public List<Class<? extends jdk.internal.event.Event>> getAllEventClasses() {
        return JfrJavaEvents.getAllEventClasses();
    }

    /** See {@link JVM#getUnloadedEventClassCount}. */
    @Substitute
    public long getUnloadedEventClassCount() {
        return 0;
    }

    /** See {@link JVM#getClassId}. Intrinsified on HotSpot. */
    @Substitute
    @Uninterruptible(reason = "Needed for SubstrateJVM.getClassId().")
    public static long getClassId(Class<?> clazz) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }

        /*
         * The result is only valid until the epoch changes but this is fine because EventWriter
         * instances are invalidated when the epoch changes.
         */
        return SubstrateJVM.get().getClassId(clazz);
    }

    /** See {@link JVM#getPid}. */
    @Substitute
    public String getPid() {
        long id = ProcessProperties.getProcessID();
        return String.valueOf(id);
    }

    /** See {@link JVM#getStackTraceId}. */
    @Substitute
    @Uninterruptible(reason = "Needed for SubstrateJVM.getStackTraceId().")
    public long getStackTraceId(int skipCount) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }

        /*
         * The result is only valid until the epoch changes but this is fine because EventWriter
         * instances are invalidated when the epoch changes.
         */
        return SubstrateJVM.get().getStackTraceId(skipCount);
    }

    /** See {@link JVM#getThreadId}. */
    @Substitute
    public long getThreadId(Thread t) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.getThreadId(t);
    }

    /** See {@link JVM#getTicksFrequency}. */
    @Substitute
    public long getTicksFrequency() {
        return JfrTicks.getTicksFrequency();
    }

    /** See {@link JVM#log}. */
    @Substitute
    public static void log(int tagSetId, int level, String message) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().log(tagSetId, level, message);
    }

    /** See {@link JVM#logEvent}. */
    @Substitute
    public static void logEvent(int level, String[] lines, boolean system) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().logEvent(level, lines, system);
    }

    /** See {@link JVM#subscribeLogLevel}. */
    @Substitute
    public static void subscribeLogLevel(LogTag lt, int tagSetId) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().subscribeLogLevel(lt, tagSetId);
    }

    /** See {@link JVM#retransformClasses}. */
    @Substitute
    public synchronized void retransformClasses(Class<?>[] classes) {
        // Not supported but this method is called during JFR startup, so we can't throw an error.
    }

    /** See {@link JVM#setEnabled}. */
    @Substitute
    public void setEnabled(long eventTypeId, boolean enabled) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setFileNotification}. */
    @Substitute
    public void setFileNotification(long delta) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setFileNotification(delta);
    }

    /** See {@link JVM#setGlobalBufferCount}. */
    @Substitute
    public void setGlobalBufferCount(long count) throws IllegalArgumentException, IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setGlobalBufferCount(count);
    }

    /** See {@link JVM#setGlobalBufferSize}. */
    @Substitute
    public void setGlobalBufferSize(long size) throws IllegalArgumentException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setGlobalBufferSize(size);
    }

    /** See {@link JVM#setMemorySize}. */
    @Substitute
    public void setMemorySize(long size) throws IllegalArgumentException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setMemorySize(size);
    }

    /** See {@code JVM#setMethodSamplingPeriod}. */
    @Substitute
    public void setMethodSamplingPeriod(long type, long intervalMillis) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setMethodSamplingInterval(type, intervalMillis);
    }

    /** See {@link JVM#setOutput}. */
    @Substitute
    public void setOutput(String file) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setOutput(file);
    }

    /** See {@link JVM#setForceInstrumentation}. */
    @Substitute
    public void setForceInstrumentation(boolean force) {
    }

    /** See {@link JVM#setCompressedIntegers}. */
    @Substitute
    public void setCompressedIntegers(boolean compressed) throws IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setCompressedIntegers(compressed);
    }

    /** See {@link JVM#setStackDepth}. */
    @Substitute
    public void setStackDepth(int depth) throws IllegalArgumentException, IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setStackDepth(depth);
    }

    /** See {@link JVM#setStackTraceEnabled}. */
    @Substitute
    public void setStackTraceEnabled(long eventTypeId, boolean enabled) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setStackTraceEnabled(eventTypeId, enabled);
    }

    /** See {@link JVM#setThreadBufferSize}. */
    @Substitute
    public void setThreadBufferSize(long size) throws IllegalArgumentException, IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setThreadBufferSize(size);
    }

    /** See {@link JVM#setThreshold}. */
    @Substitute
    public boolean setThreshold(long eventTypeId, long ticks) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setThreshold(eventTypeId, ticks);
    }

    /** See {@link JVM#storeMetadataDescriptor}. */
    @Substitute
    public void storeMetadataDescriptor(byte[] bytes) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().storeMetadataDescriptor(bytes);
    }

    /** See {@link JVM#getAllowedToDoEventRetransforms}. */
    @Substitute
    public boolean getAllowedToDoEventRetransforms() {
        return false;
    }

    /** See {@link JVM#createJFR}. */
    @Substitute
    private boolean createJFR(boolean simulateFailure) throws IllegalStateException {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().createJFR(simulateFailure);
    }

    /** See {@link JVM#destroyJFR}. */
    @Substitute
    private boolean destroyJFR() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().destroyJFR();
    }

    /** See {@link JVM#isAvailable}. */
    @Substitute
    public boolean isAvailable() {
        return HasJfrSupport.get();
    }

    /** See {@link JVM#getTimeConversionFactor}. */
    @Substitute
    public double getTimeConversionFactor() {
        return 1;
    }

    /** See {@link JVM#getTypeId(Class)}. */
    @Substitute
    public long getTypeId(Class<?> clazz) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return JfrTraceId.getTraceId(clazz);
    }

    /** See {@link JVM#getEventWriter}. */
    @Substitute
    public static Object getEventWriter() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getEventWriter();
    }

    /** See {@link JVM#newEventWriter}. */
    @Substitute
    public static Target_jdk_jfr_internal_event_EventWriter newEventWriter() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().newEventWriter();
    }

    /** See {@link JVM#flush}. */
    @Substitute
    public static void flush(Target_jdk_jfr_internal_event_EventWriter writer, int uncommittedSize, int requestedSize) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().flush(writer, uncommittedSize, requestedSize);
    }

    /** See {@link JVM#flush()}. */
    @Substitute
    public void flush() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().flush();
    }

    /** See {@link JVM#commit}. */
    @Substitute
    public static long commit(long nextPosition) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().commit(nextPosition);
    }

    /** See {@link JVM#setRepositoryLocation}. */
    @Substitute
    public void setRepositoryLocation(String dirText) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setRepositoryLocation(dirText);
    }

    /** See {@code JVM#setDumpPath(String)}. */
    @Substitute
    public void setDumpPath(String dumpPathText) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().setDumpPath(dumpPathText);
    }

    /** See {@code JVM#getDumpPath()}. */
    @Substitute
    public String getDumpPath() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getDumpPath();
    }

    /** See {@link JVM#abort}. */
    @Substitute
    public void abort(String errorMsg) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        SubstrateJVM.get().abort(errorMsg);
    }

    /** See {@link JVM#addStringConstant}. */
    @Substitute
    public static boolean addStringConstant(long id, String s) {
        return false;
    }

    /** See {@link JVM#uncaughtException}. */
    @Substitute
    public void uncaughtException(Thread thread, Throwable t) {
        // Would be used to determine the emergency dump filename if an exception happens during
        // shutdown.
    }

    /** See {@link JVM#setCutoff}. */
    @Substitute
    public boolean setCutoff(long eventTypeId, long cutoffTicks) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setCutoff(eventTypeId, cutoffTicks);
    }

    @Substitute
    public boolean setThrottle(long eventTypeId, long eventSampleSize, long periodMs) {
        // Not supported but this method is called during JFR startup, so we can't throw an error.
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return true;
    }

    /** See {@link JVM#emitOldObjectSamples}. */
    @Substitute
    public void emitOldObjectSamples(long cutoff, boolean emitAll, boolean skipBFS) {
        // Not supported but this method is called during JFR shutdown, so we can't throw an error.
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
    }

    /** See {@link JVM#shouldRotateDisk}. */
    @Substitute
    public boolean shouldRotateDisk() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().shouldRotateDisk();
    }

    /** See {@link JVM#include}. */
    @Substitute
    public void include(Thread thread) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        JfrThreadLocal.setExcluded(thread, false);
    }

    /** See {@link JVM#exclude}. */
    @Substitute
    public void exclude(Thread thread) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        JfrThreadLocal.setExcluded(thread, true);
    }

    /** See {@link JVM#isExcluded(Thread)}. */
    @Substitute
    public boolean isExcluded(Thread thread) {
        if (!HasJfrSupport.get()) {
            return true;
        }
        return JfrThreadLocal.isThreadExcluded(thread);
    }

    /** See {@link JVM#isExcluded(Class)}. */
    @Substitute
    public boolean isExcluded(Class<? extends jdk.internal.event.Event> eventClass) {
        /* For now, assume that event classes are only excluded if JFR support is disabled. */
        return !HasJfrSupport.get();
    }

    /** See {@link JVM#isInstrumented}. */
    @Substitute
    public boolean isInstrumented(Class<? extends jdk.internal.event.Event> eventClass) {
        /*
         * Assume that event classes are instrumented if JFR support is present. This method should
         * ideally check for blessed commit methods in the event class, see GR-41200.
         */
        return HasJfrSupport.get();
    }

    /** See {@link JVM#getChunkStartNanos}. */
    @Substitute
    public long getChunkStartNanos() {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getChunkStartNanos();
    }

    /** See {@link JVM#setConfiguration}. */
    @Substitute
    public boolean setConfiguration(Class<? extends jdk.internal.event.Event> eventClass, Target_jdk_jfr_internal_event_EventConfiguration configuration) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().setConfiguration(eventClass, configuration);
    }

    /** See {@link JVM#getConfiguration}. */
    @Substitute
    public Object getConfiguration(Class<? extends jdk.internal.event.Event> eventClass) {
        if (!HasJfrSupport.get()) {
            throw jfrNotSupportedException();
        }
        return SubstrateJVM.get().getConfiguration(eventClass);
    }

    /** See {@link JVM#getTypeId(String)}. */
    @Substitute
    public long getTypeId(String name) {
        /* Not implemented at the moment. */
        return -1;
    }

    @Substitute
    public boolean isContainerized() {
        return Containers.isContainerized();
    }

    @Substitute
    public long hostTotalMemory() {
        /* Not implemented at the moment. */
        return 0;
    }
}

class Target_jdk_jfr_internal_JVM_Util {
    static UnsupportedOperationException jfrNotSupportedException() {
        throw new UnsupportedOperationException(VMInspectionOptions.getJfrNotSupportedMessage());
    }
}
