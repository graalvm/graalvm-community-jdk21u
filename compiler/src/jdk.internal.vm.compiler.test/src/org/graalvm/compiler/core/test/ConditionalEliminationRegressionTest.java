/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.core.common.type.StampFactory.objectNonNull;

import java.util.List;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.GuardPhiNode;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.InlineOnlyInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConditionalEliminationPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.HighTierLoweringPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public class ConditionalEliminationRegressionTest extends GraalCompilerTest {

    private final SpeculationLog speculationLog;

    @SuppressWarnings("this-escape")
    public ConditionalEliminationRegressionTest() {
        speculationLog = getCodeCache().createSpeculationLog();
    }

    @Override
    protected SpeculationLog getSpeculationLog() {
        speculationLog.collectFailedSpeculations();
        return speculationLog;
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.ALL;
    }

    public static void deoptimizeAndInvalidateUnreached() {
    }

    public static <T> T guardingBoolean(T value, boolean condition, boolean negated) {
        if (negated) {
            if (!condition) {
                GraalDirectives.deoptimize();
            }
        } else {
            if (condition) {
                GraalDirectives.deoptimize();
            }
        }
        return value;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        Registration r = new Registration(conf.getPlugins().getInvocationPlugins(), ConditionalEliminationRegressionTest.class);
        r.register(new InlineOnlyInvocationPlugin("deoptimizeAndInvalidateUnreached") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateReprofile, DeoptimizationReason.UnreachedCode));
                return true;
            }
        });
        r.register(new InlineOnlyInvocationPlugin("guardingBoolean", Object.class, boolean.class, boolean.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value, ValueNode condition, ValueNode negatedConstant) {
                if (!(condition instanceof ConditionalNode)) {
                    return false;
                }
                LogicNode l = ((ConditionalNode) condition).condition();
                boolean isNullCheck = l instanceof IsNullNode;
                boolean negated = ((PrimitiveConstant) negatedConstant.asConstant()).asInt() == 1;

                GuardingNode guardingNode;
                if (isNullCheck) {
                    guardingNode = b.append(new FixedGuardNode(l, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateRecompile, negated));
                } else {
                    guardingNode = b.append(new FixedGuardNode(l, DeoptimizationReason.ClassCastException, DeoptimizationAction.InvalidateRecompile, negated));
                }
                b.addPush(value.getStackKind(), PiNode.create(value, objectNonNull(), guardingNode.asNode()));
                return true;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }

    static class FieldVal {
        int x;
    }

    // abstract base class for non exact type checks and null check
    abstract static class A {
    }

    // with an implementation for check casts
    static class B extends A {
        FieldVal field;
    }

    // and another sub class to avoid exact checks
    static class C extends B {

    }

    static void snippet(Object o) {
        for (int i = 0; i < 500; i++) {
            // BELOW is the problematic code, the code above just exists so we can hoist the wrong
            // pi out of a loop then

            boolean b = o == null;
            if (GraalDirectives.injectBranchProbability(0.001, b)) {
                deoptimizeAndInvalidateUnreached();
            }

            if (!GraalDirectives.injectBranchProbability(0.001, o instanceof A)) {
                GraalDirectives.deoptimizeAndInvalidate();
            }

            Object nullCheckedObject = guardingBoolean(o, b, true);

            boolean b1 = nullCheckedObject instanceof A;

            @SuppressWarnings("unused")
            Object nullCheckedSuperClassChecked = guardingBoolean(nullCheckedObject, b1, false);

            B bObjectMaybeNull = (B) o; // instanceof with null

            boolean b2 = bObjectMaybeNull == null;

            B bObjectNotNull = guardingBoolean(bObjectMaybeNull, b2, true);

            int res = bObjectNotNull.field.x;

            S = res;

        }
    }

    static int S;

    @Test
    public void test01() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        for (int i = 0; i < 100; i++) {
            B b = new B();
            b.field = new FieldVal();
            snippet(b);
            C c = new C();
            c.field = new FieldVal();
            snippet(c);
        }
        try {
            snippet(null);
            Assert.fail("Must not reach here");
        } catch (Throwable t) {
            // swallow the NPE, we want the test to segfault later if a wrong conditional
            // elimination is done
        }
        B b = new B();
        b.field = new FieldVal();
        test(opt, "snippet", b);
        test(opt, "snippet", new ArgSupplier() {

            @Override
            public Object get() {
                return null;
            }
        });
        test(opt, "snippet", new ArgSupplier() {

            @Override
            public Object get() {
                return null;
            }
        });
    }

    @BytecodeParserNeverInline
    @SuppressWarnings("unused")
    private static boolean foldAferRealInline(int i, int j) {
        return i < j;
    }

    public static int loopSnippet(boolean b, int limit1, int limit2) {
        block: {
            if (b) {
                for (int i = 0; i < limit1; i++) {
                    GraalDirectives.sideEffect(i);
                    if (i < limit2) {
                        GraalDirectives.sideEffect(limit2);
                        // folds away after inlining and CE
                        if (foldAferRealInline(i, limit2)) {
                            break block;
                        }
                    }
                }
            } else {
                for (int i = 0; i < limit2; i++) {
                    GraalDirectives.sideEffect(i);
                }
            }
        }
        return S;
    }

    @Test
    public void testProxyGen() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("loopSnippet"), AllowAssumptions.YES);
        // make fixed guards
        new ConvertDeoptimizeToGuardPhase(CanonicalizerPhase.create()).apply(g, getDefaultHighTierContext());

        // inline the trivial optimizable
        new InliningPhase(new GreedyInliningPolicy(null), CanonicalizerPhase.create()).apply(g, getEagerHighTierContext());

        // get floating guards
        new HighTierLoweringPhase(CanonicalizerPhase.create()).apply(g, getDefaultHighTierContext());

        new FloatingReadPhase(CanonicalizerPhase.create()).apply(g, getDefaultMidTierContext());

        for (Node n : g.getNodes()) {
            if (n instanceof MergeNode) {
                MergeNode merge = (MergeNode) n;
                // must be the merge of the loop exits

                GuardPhiNode guardPhi = g.addWithoutUnique(new GuardPhiNode(merge));
                for (EndNode end : merge.forwardEnds()) {
                    guardPhi.addInput(AbstractBeginNode.prevBegin(end));
                }
                List<FloatingReadNode> fr = g.getNodes().filter(FloatingReadNode.class).snapshot();
                assert fr.size() == 1 : "Must only have a single floating read found " + fr;
                fr.get(0).setGuard(guardPhi);
            }
        }

        g.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, g, "After creating guard phi");
        new ConditionalEliminationPhase(false).apply(g, getDefaultMidTierContext());

        GraphOrder.assertSchedulableGraph(g);
    }
}
