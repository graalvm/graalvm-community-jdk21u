/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativebridge.processor.test.nativetohs;

import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.nativebridge.GenerateNativeToHotSpotBridge;
import org.graalvm.nativebridge.Out;
import org.graalvm.nativebridge.processor.test.OutParameterService;
import org.graalvm.nativebridge.processor.test.TestJNIConfig;

import java.util.List;
import java.util.Map;

@GenerateNativeToHotSpotBridge(jniConfig = TestJNIConfig.class)
abstract class HSOutParameterService extends HSObject implements OutParameterService {

    HSOutParameterService(JNIEnv env, JObject handle) {
        super(env, handle);
    }

    @Override
    public abstract int singleOutParameterPrimitive(@Out List<String> p1);

    @Override
    public abstract int[] singleOutParameterArray(@Out List<String> p1);

    @Override
    public abstract void singleOutParameterVoid(@Out List<String> p1);

    @Override
    public abstract Map<String, String> singleOutParameterCustom(@Out List<String> p1);

    @Override
    public abstract int multipleOutParametersPrimitive(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract int[] multipleOutParametersArray(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract void multipleOutParametersVoid(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract Map<String, String> multipleOutParametersCustom(@Out List<String> p1, @Out List<String> p2);

    @Override
    public abstract void mixedParametersVoid(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);

    @Override
    public abstract int mixedParametersPrimitive(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);

    @Override
    public abstract int[] mixedParametersArray(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);

    @Override
    public abstract Map<String, String> mixedParametersCustom(List<String> p1, @Out List<String> p2, List<String> p3, @Out List<String> p4);
}
