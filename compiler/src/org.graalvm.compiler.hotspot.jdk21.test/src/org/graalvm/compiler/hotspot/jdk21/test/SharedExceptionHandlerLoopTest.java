/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.jdk21.test;

import java.lang.constant.ClassDesc;

import org.junit.Test;

import org.graalvm.compiler.core.test.CustomizedBytecodePatternTest;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests a bytecode pattern where two ExceptionDispatchBlocks - one inside a loop and one outside -
 * dispatch to the same exception handler. Such a pattern cannot be produced by javac from plain
 * Java code, but the Kotlin compiler can produce such patterns when compiling coroutines. See
 * {@link #generateClass} for the Java source code from which the modified bytecode is derived.
 */
public class SharedExceptionHandlerLoopTest extends CustomizedBytecodePatternTest {

    @Test
    public void test() throws ClassNotFoundException {
        Class<?> testClass = getClass("TestClass");
        ResolvedJavaMethod method = getResolvedJavaMethod(testClass, "testMethod");
        compile(method, null);

        /*
         * Returns o1.toString().
         */
        test(method, null, new Object(), new NPEThrower());
        /*
         * Returns null because o1.toString() throws an NPE.
         */
        test(method, null, new NPEThrower(), new NPEThrower());
        /*
         * Exits the loop because o1.toString() throws an IAE, returns null because o2.toString()
         * throws a NPE.
         */
        test(method, null, new IAEThrower(), new NPEThrower());
        /*
         * Exits the loop because o1.toString() throws an IAE, returns o2.toString().
         */
        test(method, null, new IllegalArgumentException(), new Object());
    }

    public static class NPEThrower {
        @Override
        public String toString() {
            throw new NullPointerException();
        }
    }

    public static class IAEThrower {
        @Override
        public String toString() {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Produces bytecode which resembles the following Java code except that both NPE-catches
     * dispatch to the same handler. The first exception dispatch happens from inside the loop, the
     * second from outside.
     *
     * <pre>
     * public class TestClass {
     *     public static Object testMethod(Object o1, Object o2) {
     *         for (;;) {
     *             try {
     *                 return o1.toString();
     *             } catch (NullPointerException e) {
     *                 // same NPE handler as below. dispatch block inside loop
     *                 return null;
     *             } catch (SecurityException e) {
     *                 // continue (endless loop)
     *             } catch (Exception e) {
     *                 break;
     *             }
     *         }
     *
     *         try {
     *             return o2.toString();
     *         } catch (NullPointerException npe) {
     *             // same NPE handler as above. dispatch block outside loop
     *             return null;
     *         }
     *     }
     * }
     * </pre>
     */
    @Override
    protected byte[] generateClass(String internalClassName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, internalClassName, null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        "testMethod",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                        null,
                        new String[]{"java/lang/NullPointerException", "java/lang/IllegalAccessError", "java/lang/Exception"});
        mv.visitCode();

        Label start = new Label();
        Label loopExcEnd = new Label();
        Label loopNPEHandler = new Label();
        Label loopIAEHandler = new Label();
        Label loopEHandler = new Label();
        Label afterLoopExcStart = new Label();
        Label afterLoopExcEnd = new Label();
        Label retLabel = new Label();

        mv.visitTryCatchBlock(start, loopExcEnd, loopNPEHandler, "java/lang/NullPointerException");
        mv.visitTryCatchBlock(start, loopExcEnd, loopIAEHandler, "java/lang/IllegalAccessError");
        mv.visitTryCatchBlock(start, loopExcEnd, loopEHandler, "java/lang/Exception");
        mv.visitTryCatchBlock(afterLoopExcStart, afterLoopExcEnd, loopNPEHandler, "java/lang/NullPointerException");

        mv.visitLabel(start);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        mv.visitLabel(loopExcEnd);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitLabel(loopNPEHandler);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitLabel(loopIAEHandler);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitJumpInsn(Opcodes.GOTO, start);

        mv.visitLabel(loopEHandler);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitJumpInsn(Opcodes.GOTO, retLabel);

        mv.visitLabel(retLabel);
        mv.visitLabel(afterLoopExcStart);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;", false);
        mv.visitLabel(afterLoopExcEnd);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static ClassDesc cd(Class<?> klass) {
        return klass.describeConstable().orElseThrow();
    }
}
