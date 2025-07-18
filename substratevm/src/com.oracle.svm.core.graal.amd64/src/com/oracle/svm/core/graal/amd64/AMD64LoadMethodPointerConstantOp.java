/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.StandardOp;
import org.graalvm.compiler.lir.amd64.AMD64LIRInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import org.graalvm.nativeimage.Platform;

public final class AMD64LoadMethodPointerConstantOp extends AMD64LIRInstruction implements StandardOp.LoadConstantOp {
    public static final LIRInstructionClass<AMD64LoadMethodPointerConstantOp> TYPE = LIRInstructionClass.create(AMD64LoadMethodPointerConstantOp.class);
    private final SubstrateMethodPointerConstant constant;
    @Def({REG, HINT}) private AllocatableValue result;

    AMD64LoadMethodPointerConstantOp(AllocatableValue result, SubstrateMethodPointerConstant constant) {
        super(TYPE);
        this.constant = constant;
        this.result = result;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register resultReg = asRegister(result);
        if (!Platform.includedIn(Platform.DARWIN_AMD64.class)) {
            crb.recordInlineDataInCode(constant);
            masm.movq(resultReg, 0L, true);
        } else {
            /* [GR-43389] ld64 bug does not allow direct8 relocations in .text on darwin */
            masm.movq(resultReg, (AMD64Address) crb.recordDataReferenceInCode(constant, FrameAccess.wordSize()));
        }
    }

    @Override
    public AllocatableValue getResult() {
        return result;
    }

    @Override
    public SubstrateMethodPointerConstant getConstant() {
        return constant;
    }

    @Override
    public boolean canRematerializeToStack() {
        return false;
    }
}
