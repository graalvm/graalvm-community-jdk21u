/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.nodes;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.replacements.nodes.MacroNode.MacroParams;
import org.graalvm.compiler.replacements.nodes.ReflectionGetCallerClassNode;

import com.oracle.svm.core.jdk.StackTraceUtils;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@NodeInfo
public final class SubstrateReflectionGetCallerClassNode extends ReflectionGetCallerClassNode {

    public static final NodeClass<SubstrateReflectionGetCallerClassNode> TYPE = NodeClass.create(SubstrateReflectionGetCallerClassNode.class);

    public SubstrateReflectionGetCallerClassNode(MacroParams p) {
        super(TYPE, p);
    }

    @Override
    protected boolean isCallerSensitive(ResolvedJavaMethod method) {
        /*
         * The SVM implementation of getCallerClass does not check for the @CallerSensitive
         * annotation, so we disable this check for the intrinsification too.
         */
        return true;
    }

    @Override
    protected boolean ignoredBySecurityStackWalk(MetaAccessProvider metaAccess, ResolvedJavaMethod method) {
        return StackTraceUtils.ignoredBySecurityStackWalk(metaAccess, method);
    }
}
