/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

//Checkstyle: stop

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.AtomicFieldUpdaterOffset;
import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.Reset;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.CharsetDecoder;
import java.security.AccessControlContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/*
 * This file contains JDK fields that need to be intercepted because their value in the hosted environment is not
 * suitable for the Substrate VM. The list is derived from the intercepted fields of the Maxine VM.
 */

@TargetClass(java.nio.charset.CharsetEncoder.class)
final class Target_java_nio_charset_CharsetEncoder {
    @Alias @RecomputeFieldValue(kind = Reset) //
    private WeakReference<CharsetDecoder> cachedDecoder;
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicReferenceFieldUpdater$AtomicReferenceFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicReferenceFieldUpdater_AtomicReferenceFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;

    /** class holding the field */
    @Alias private Class<?> tclass;

    /** field value type */
    @Alias private Class<?> vclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicReferenceFieldUpdater_AtomicReferenceFieldUpdaterImpl(
                    final Class<?> tclass, final Class<?> vclass, final String fieldName, final Class<?> caller) {
        final Field field;
        final Class<?> fieldClass;
        final int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);

            modifiers = field.getModifiers();
            fieldClass = field.getType();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (vclass != fieldClass) {
            throw new ClassCastException();
        }
        if (vclass.isPrimitive())
            throw new IllegalArgumentException("Must be reference type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.vclass = vclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicIntegerFieldUpdater$AtomicIntegerFieldUpdaterImpl")
final class Target_java_util_concurrent_atomic_AtomicIntegerFieldUpdater_AtomicIntegerFieldUpdaterImpl {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;
    /** class holding the field */
    @Alias private Class<?> tclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicIntegerFieldUpdater_AtomicIntegerFieldUpdaterImpl(final Class<?> tclass,
                    final String fieldName, final Class<?> caller) {
        final Field field;
        final int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (field.getType() != int.class)
            throw new IllegalArgumentException("Must be integer type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }
}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$CASUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;
    /** class holding the field */
    @Alias private Class<?> tclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_CASUpdater(final Class<?> tclass,
                    final String fieldName, final Class<?> caller) {
        final Field field;
        final int modifiers;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (field.getType() != long.class)
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }

}

@TargetClass(className = "java.util.concurrent.atomic.AtomicLongFieldUpdater$LockedUpdater")
final class Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_LockedUpdater {
    @Alias @RecomputeFieldValue(kind = AtomicFieldUpdaterOffset) //
    private long offset;

    /** the same as tclass, used for checks */
    @Alias private Class<?> cclass;
    /** class holding the field */
    @Alias private Class<?> tclass;

    // simplified version of the original constructor
    @SuppressWarnings("unused")
    @Substitute
    Target_java_util_concurrent_atomic_AtomicLongFieldUpdater_LockedUpdater(final Class<?> tclass,
                    final String fieldName, final Class<?> caller) {
        Field field = null;
        int modifiers = 0;
        try {
            field = tclass.getDeclaredField(fieldName);
            modifiers = field.getModifiers();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        if (field.getType() != long.class)
            throw new IllegalArgumentException("Must be long type");

        if (!Modifier.isVolatile(modifiers))
            throw new IllegalArgumentException("Must be volatile type");

        // access checks are disabled
        this.cclass = tclass;
        this.tclass = tclass;
        this.offset = Unsafe.getUnsafe().objectFieldOffset(field);
    }
}

/**
 * The atomic field updaters access fields using {@code Unsafe}. The static analysis needs to know
 * about all these fields, so we need to find the original field (the updater only stores the field
 * offset) and mark it as unsafe accessed.
 */
@AutomaticallyRegisteredFeature
class AtomicFieldUpdaterFeature implements InternalFeature {

    private final ConcurrentMap<Object, Boolean> processedUpdaters = new ConcurrentHashMap<>();
    private Consumer<Field> markAsUnsafeAccessed;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(this::processObject);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        markAsUnsafeAccessed = (field -> access.registerAsUnsafeAccessed(field));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        markAsUnsafeAccessed = null;
    }

    private Object processObject(Object obj) {
        if (obj instanceof AtomicReferenceFieldUpdater || obj instanceof AtomicIntegerFieldUpdater || obj instanceof AtomicLongFieldUpdater) {
            if (processedUpdaters.putIfAbsent(obj, true) == null) {
                processFieldUpdater(obj);
            }
        }
        return obj;
    }

    /*
     * This code runs multi-threaded during the static analysis. It must not be called after static
     * analysis, because that would mean that we missed an atomic field updater during static
     * analysis.
     */
    private void processFieldUpdater(Object updater) {
        VMError.guarantee(markAsUnsafeAccessed != null, "New atomic field updater found after static analysis");

        Class<?> updaterClass = updater.getClass();
        Class<?> tclass = ReflectionUtil.readField(updaterClass, "tclass", updater);
        long searchOffset = ReflectionUtil.readField(updaterClass, "offset", updater);
        // search the declared fields for a field with a matching offset
        for (Field f : tclass.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                long fieldOffset = Unsafe.getUnsafe().objectFieldOffset(f);
                if (fieldOffset == searchOffset) {
                    markAsUnsafeAccessed.accept(f);
                    return;
                }
            }
        }
        throw VMError.shouldNotReachHere("unknown field offset class: " + tclass + ", offset = " + searchOffset);
    }
}

@AutomaticallyRegisteredFeature
@Platforms(InternalPlatform.NATIVE_ONLY.class)
class InnocuousForkJoinWorkerThreadFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.lookup(RuntimeClassInitializationSupport.class).rerunInitialization(access.findClassByName("java.util.concurrent.ForkJoinWorkerThread$InnocuousForkJoinWorkerThread"),
                        "innocuousThreadGroup must be initialized at run time");
    }
}

@TargetClass(java.util.concurrent.ForkJoinPool.class)
@SuppressWarnings("unused") //
final class Target_java_util_concurrent_ForkJoinPool {

    /*
     * Recomputation of the common pool: we cannot create it during image generation, because at
     * this time we do not know the number of cores that will be available at run time. Therefore,
     * we create the common pool when it is accessed the first time.
     *
     * Note that re-running the class initializer of ForkJoinPool does not work because the class
     * initializer does several other things that we do not support at run time.
     */
    @Alias @InjectAccessors(ForkJoinPoolCommonAccessor.class) //
    static ForkJoinPool common;

    @Substitute
    public static int getCommonPoolParallelism() {
        /*
         * The field for common parallelism is only accessed via this method, so a substitution
         * provides a convenient place to ensure that the common pool is initialized.
         */
        return ForkJoinPoolCommonAccessor.get().getParallelism();
    }

    /* Delete the original static field for common parallelism. */
    @Delete //
    @TargetElement(onlyWith = JDK17OrEarlier.class)//
    static int COMMON_PARALLELISM;

    @Alias @TargetElement(onlyWith = JDK19OrLater.class) //
    private static Unsafe U;

    @Alias @TargetElement(onlyWith = JDK19OrLater.class) //
    private static long POOLIDS;

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class) //
    private static int getAndAddPoolIds(int x) {
        // Original method wrongly uses ForkJoinPool.class instead of calling U.staticFieldBase()
        return U.getAndAddInt(StaticFieldsSupport.getStaticPrimitiveFields(), POOLIDS, x);
    }

    @Alias //
    Target_java_util_concurrent_ForkJoinPool(byte forCommonPoolOnly) {
    }
}

/**
 * An injected field to replace ForkJoinPool.common.
 *
 * This class is also a convenient place to handle the initialization of "common" and
 * "commonParallelism", which can unfortunately be accessed independently.
 */
class ForkJoinPoolCommonAccessor {

    /**
     * The static field that is used in place of the static field ForkJoinPool.common. This field
     * set the first time it is accessed, when it transitions from null to something, but does not
     * change thereafter.
     */
    private static volatile ForkJoinPool injectedCommon;

    static volatile int commonParallelism;

    /** The get access method for ForkJoinPool.common. */
    static ForkJoinPool get() {
        ForkJoinPool result = injectedCommon;
        if (result == null) {
            result = initializeCommonPool();
        }
        return result;
    }

    private static synchronized ForkJoinPool initializeCommonPool() {
        ForkJoinPool result = injectedCommon;
        if (result == null) {
            result = SubstrateUtil.cast(new Target_java_util_concurrent_ForkJoinPool((byte) 0), ForkJoinPool.class);
            injectedCommon = result;
        }
        return result;
    }
}

@TargetClass(value = java.util.concurrent.ForkJoinPool.class, innerClass = "DefaultForkJoinWorkerThreadFactory", onlyWith = JDK21OrLater.class)
@SuppressWarnings("removal")
final class Target_java_util_concurrent_ForkJoinPool_DefaultForkJoinWorkerThreadFactory {
    @Alias @RecomputeFieldValue(kind = Reset) //
    static AccessControlContext regularACC;
    @Alias @RecomputeFieldValue(kind = Reset) //
    static AccessControlContext commonACC;
}

/** Dummy class to have a class with the file's name. */
public final class RecomputedFields {
}
