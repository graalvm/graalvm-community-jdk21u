/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import java.util.function.Consumer;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.SyncPort;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

/**
 * Emits spin wait instruction(s).
 */
// @formatter:off
@SyncPort(from = "https://github.com/openjdk/jdk/blob/d7b941640638b35f9ac1ef11cd6bf6ccb795c29a/src/hotspot/cpu/aarch64/vm_version_aarch64.cpp#L52-L68",
          sha1 = "92f81ed500658553a2ef2e7c48633094d95ba974")
// @formatter:on
@Opcode("SPIN_WAIT")
public final class AArch64SpinWaitOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64SpinWaitOp> TYPE = LIRInstructionClass.create(AArch64SpinWaitOp.class);

    private final Consumer<AArch64MacroAssembler> instruction;
    private final int count;

    public AArch64SpinWaitOp(Consumer<AArch64MacroAssembler> instruction, int count) {
        super(TYPE);

        this.instruction = instruction;
        this.count = count;
        GraalError.guarantee(count > 0, "count should be positive");
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        for (int i = 0; i < count; i++) {
            instruction.accept(masm);
        }
    }
}
