/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.thread;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.posix.PosixUtils;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.Sched;
import com.oracle.svm.core.posix.headers.Time;
import com.oracle.svm.core.posix.headers.Time.timespec;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.posix.linux.LinuxLibCHelper;
import com.oracle.svm.core.posix.pthread.PthreadVMLockSupport;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredImageSingleton(VMThreads.class)
public final class PosixVMThreads extends VMThreads {
    @Fold
    public static PosixVMThreads singleton() {
        return (PosixVMThreads) ImageSingletons.lookup(VMThreads.class);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public OSThreadHandle getCurrentOSThreadHandle() {
        return Pthread.pthread_self();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected OSThreadId getCurrentOSThreadId() {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            Pthread.pthread_t pthread = Pthread.pthread_self();
            return WordFactory.unsigned(DarwinPthread.pthread_mach_thread_np(pthread));
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            int result = LinuxLibCHelper.getThreadId();
            VMError.guarantee(result != -1, "SYS_gettid failed");
            return WordFactory.signed(result);
        }

        throw VMError.unsupportedFeature("PosixVMThreads.getCurrentOSThreadId() on unknown OS");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected void joinNoTransition(OSThreadHandle osThreadHandle) {
        Pthread.pthread_t pthread = (Pthread.pthread_t) osThreadHandle;
        PosixUtils.checkStatusIs0(Pthread.pthread_join_no_transition(pthread, WordFactory.nullPointer()), "Pthread.joinNoTransition");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public void nativeSleep(int milliseconds) {
        timespec ts = StackValue.get(timespec.class);
        ts.set_tv_sec(milliseconds / TimeUtils.millisPerSecond);
        ts.set_tv_nsec((milliseconds % TimeUtils.millisPerSecond) * TimeUtils.nanosPerMilli);
        Time.NoTransitions.nanosleep(ts, WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public void yield() {
        Sched.NoTransitions.sched_yield();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean supportsNativeYieldAndSleep() {
        return true;
    }

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    protected boolean initializeOnce() {
        return PthreadVMLockSupport.initialize();
    }

    interface FILE extends PointerBase {
    }

    @CFunction(value = "fdopen", transition = Transition.NO_TRANSITION)
    private static native FILE fdopen(int fd, CCharPointer mode);

    @CFunction(value = "fprintfSD", transition = Transition.NO_TRANSITION)
    private static native int fprintfSD(FILE stream, CCharPointer format, CCharPointer arg0, int arg1);

    private static final CGlobalData<CCharPointer> FAIL_FATALLY_FDOPEN_MODE = CGlobalDataFactory.createCString("w");
    private static final CGlobalData<CCharPointer> FAIL_FATALLY_MESSAGE_FORMAT = CGlobalDataFactory.createCString("Fatal error: %s (code %d)\n");

    @Uninterruptible(reason = "Thread state not set up.")
    @Override
    public void failFatally(int code, CCharPointer message) {
        FILE stderr = fdopen(2, FAIL_FATALLY_FDOPEN_MODE.get());
        fprintfSD(stderr, FAIL_FATALLY_MESSAGE_FORMAT.get(), message, code);
        LibC.exit(code);
    }

    @AutomaticallyRegisteredImageSingleton(ThreadLookup.class)
    public static class PosixThreadLookup extends ThreadLookup {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public ComparableWord getThreadIdentifier() {
            /* Use pthread_self() instead of gettid() because it is faster. */
            return PosixVMThreads.singleton().getCurrentOSThreadHandle();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public boolean matchesThread(IsolateThread thread, ComparableWord identifier) {
            return VMThreads.OSThreadHandleTL.get(thread).equal(identifier);
        }
    }
}
