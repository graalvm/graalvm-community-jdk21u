#
# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.
#
import os
import re
import shutil
import tempfile
import difflib
import zipfile
from argparse import ArgumentParser, RawDescriptionHelpFormatter
from collections import OrderedDict
from os.path import exists, isdir, join, abspath
from urllib.parse import urljoin # pylint: disable=unused-import,no-name-in-module

import mx
import mx_benchmark
import mx_gate
import mx_native
import mx_sdk
import mx_sdk_vm
import mx_unittest
import mx_jardistribution
import mx_pomdistribution
import tck
from mx_gate import Task
from mx_javamodules import as_java_module, get_module_name
from mx_sigtest import sigtest
from mx_unittest import unittest

_suite = mx.suite('truffle')

# re-export custom mx project classes, so they can be used from suite.py
from mx_sdk_shaded import ShadedLibraryProject # pylint: disable=unused-import

class JMHRunnerTruffleBenchmarkSuite(mx_benchmark.JMHRunnerBenchmarkSuite):

    def name(self):
        return "truffle"

    def group(self):
        return "Graal"

    def subgroup(self):
        return "truffle"

    def extraVmArgs(self):
        extraVmArgs = super(JMHRunnerTruffleBenchmarkSuite, self).extraVmArgs()
        extraVmArgs.extend(_open_module_exports_args())
        # com.oracle.truffle.api.benchmark.InterpreterCallBenchmark$BenchmarkState needs DefaultTruffleRuntime
        extraVmArgs.append('--add-exports=org.graalvm.truffle/com.oracle.truffle.api.impl=ALL-UNNAMED')
        return extraVmArgs

mx_benchmark.add_bm_suite(JMHRunnerTruffleBenchmarkSuite())
#mx_benchmark.add_java_vm(mx_benchmark.DefaultJavaVm("server", "default"), priority=3)

def javadoc(args, vm=None):
    """build the Javadoc for all API packages"""
    extraArgs = mx_sdk.build_oracle_compliant_javadoc_args(_suite, 'GraalVM', 'Truffle')
    projects = [
        'org.graalvm.collections',
        'org.graalvm.word',
        'org.graalvm.options',
        'org.graalvm.nativeimage',
        'org.graalvm.home',
        'org.graalvm.polyglot',
        'com.oracle.svm.core.annotate',
        'com.oracle.truffle.api',
        'com.oracle.truffle.api.dsl',
        'com.oracle.truffle.api.profiles',
        'com.oracle.truffle.api.utilities',
        'com.oracle.truffle.api.library',
        'com.oracle.truffle.api.strings',
        'com.oracle.truffle.api.interop',
        'com.oracle.truffle.api.exception',
        'com.oracle.truffle.api.instrumentation',
        'com.oracle.truffle.api.debug',
        'com.oracle.truffle.api.object',
        'com.oracle.truffle.api.staticobject',
    ]
    mx.javadoc(['--unified', '--projects', ','.join(projects)] + extraArgs + args, includeDeps=False)
    javadoc_dir = os.sep.join([_suite.dir, 'javadoc'])
    checkLinks(javadoc_dir)

def checkLinks(javadocDir):
    href = re.compile('(?<=href=").*?(?=")')
    filesToCheck = {}
    for root, _, files in os.walk(javadocDir):
        for f in files:
            if f.endswith('.html'):
                html = os.path.join(root, f)
                content = open(html, 'r').read()
                for url in href.findall(content):
                    full = urljoin(html, url)
                    sectionIndex = full.find('#')
                    questionIndex = full.find('?')
                    minIndex = sectionIndex
                    if minIndex < 0:
                        minIndex = len(full)
                    if 0 <= questionIndex < minIndex:
                        minIndex = questionIndex
                    path = full[0:minIndex]

                    sectionNames = filesToCheck.get(path, [])
                    if sectionIndex >= 0:
                        s = full[sectionIndex + 1:]
                        sectionNames = sectionNames + [(html, s)]
                    else:
                        sectionNames = sectionNames + [(html, None)]

                    filesToCheck[path] = sectionNames

    err = False
    for referencedfile, sections in filesToCheck.items():
        if referencedfile.startswith('javascript:') or referencedfile.startswith('http:') or referencedfile.startswith('https:') or referencedfile.startswith('mailto:'):
            continue
        if not exists(referencedfile):
            mx.warn('Referenced file ' + referencedfile + ' does not exist. Referenced from ' + sections[0][0])
            err = True
        else:
            content = open(referencedfile, 'r').read()
            for path, s in sections:
                if not s is None:
                    whereName = content.find('name="' + s + '"')
                    whereId = content.find('id="' + s + '"')
                    if whereName == -1 and whereId == -1:
                        mx.warn('There should be section ' + s + ' in ' + referencedfile + ". Referenced from " + path)
                        err = True

    if err:
        mx.abort('There are wrong references in Javadoc')

def _open_module_exports_args():
    """
    Gets the VM args for exporting all Truffle API packages on JDK9 or later.
    The default Truffle moduleInfo is opened but closed version is deployed into graalvm.
    To run benchmarks on the graalvm we need to open the closed Truffle packages.
    """
    assert mx.get_jdk().javaCompliance >= '1.9'
    truffle_api_dist = mx.distribution('TRUFFLE_API')
    truffle_api_module_name = truffle_api_dist.moduleInfo['name']
    module_info_open_exports = getattr(truffle_api_dist, 'moduleInfo')['exports']
    args = []
    for export in module_info_open_exports:
        if ' to ' in export: # Qualified exports
            package, targets = export.split(' to ')
            targets = targets.replace(' ', '')
        else: # Unqualified exports
            package = export
            targets = 'ALL-UNNAMED'
        args.append('--add-exports=' + truffle_api_module_name + '/' + package + '=' + targets)
    return args

def _unittest_config_participant(config):
    vmArgs, mainClass, mainClassArgs = config
    # Disable DefaultRuntime warning
    vmArgs = vmArgs + ['-Dpolyglot.engine.WarnInterpreterOnly=false']

    # This is required to access jdk.internal.module.Modules which
    # in turn allows us to dynamically open fields/methods to reflection.
    vmArgs = vmArgs + ['--add-exports=java.base/jdk.internal.module=ALL-UNNAMED']

    mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/*=ALL-UNNAMED'])
    mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle.compiler/*=ALL-UNNAMED'])
    mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle.runtime/*=ALL-UNNAMED'])
    mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.polyglot/*=ALL-UNNAMED'])
    mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.sl/*=ALL-UNNAMED'])
    mainClassArgs.extend(['-JUnitOpenPackages', 'org.graalvm.truffle/*=org.graalvm.sl'])

    config = (vmArgs, mainClass, mainClassArgs)
    if _shouldRunTCKParticipant:
        config = _unittest_config_participant_tck(config)
    return config

mx_unittest.add_config_participant(_unittest_config_participant)

# simple utility that returns the right distribution names to use
# for building a language module path
def resolve_truffle_dist_names(use_optimized_runtime=True, use_enterprise=True):
    if use_optimized_runtime:
        enterprise_dist = _get_enterprise_truffle()
        if enterprise_dist and use_enterprise:
            return ['graal-enterprise:TRUFFLE_ENTERPRISE']
        else:
            return ['truffle:TRUFFLE_RUNTIME']
    else:
        return ['truffle:TRUFFLE_API']

def _get_enterprise_truffle():
    return mx.distribution('graal-enterprise:TRUFFLE_ENTERPRISE', False)

def resolve_sl_dist_names(use_optimized_runtime=True, use_enterprise=True):
    return ['TRUFFLE_SL', 'TRUFFLE_SL_LAUNCHER', 'TRUFFLE_NFI_LIBFFI'] + resolve_truffle_dist_names(use_optimized_runtime=use_optimized_runtime, use_enterprise=use_enterprise)

def sl(args):
    """run an SL program"""
    vm_args, sl_args = mx.extract_VM_args(args)
    return mx.run(_sl_command(mx.get_jdk(tag="graalvm"), vm_args, sl_args, force_cp=False))


def _sl_command(jdk, vm_args, sl_args, use_optimized_runtime=True, use_enterprise=True, force_cp=False):
    dist_names = resolve_sl_dist_names(use_optimized_runtime=use_optimized_runtime, use_enterprise=use_enterprise)
    if force_cp:
        main_class = ["com.oracle.truffle.sl.launcher.SLMain"]
    else:
        main_class = ["--module", "org.graalvm.sl_launcher/com.oracle.truffle.sl.launcher.SLMain"]
    return [jdk.java] + vm_args + mx.get_runtime_jvm_args(names=dist_names, force_cp=force_cp) + main_class + sl_args


def slnative(args):
    """build a native image of an SL program"""
    vm_args, sl_args = mx.extract_VM_args(args)
    target_dir = tempfile.mkdtemp()
    jdk = mx.get_jdk(tag='graalvm')
    image = _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=True, force_cp=False, hosted_assertions=False)
    if not image:
        mx.abort("No native-image installed in GraalVM {}. Switch to an environment that has an installed native-image command.".format(mx_sdk_vm.graalvm_home(fatalIfMissing=True)))
    mx.log("Image build completed. Running {}".format(" ".join([image] + sl_args)))
    result = mx.run([image] + sl_args)
    return result


def _native_image_sl(jdk, vm_args, target_dir, use_optimized_runtime=True, use_enterprise=True, force_cp=False, hosted_assertions=True):
    native_image_path = jdk.exe_path('native-image')
    if not exists(native_image_path):
        native_image_path = os.path.join(jdk.home, 'bin', mx.cmd_suffix('native-image'))
        if not exists(native_image_path):
            mx.warn("No native-image installed in GraalVM {}. Switch to an environment that has an installed native-image command.".format(jdk.home))
            return None

    target_path = os.path.join(target_dir, mx.exe_suffix('sl'))
    dist_names = resolve_sl_dist_names(use_optimized_runtime=use_optimized_runtime, use_enterprise=use_enterprise)

    if hosted_assertions:
        vm_args += ["-J-ea", "-J-esa"]

    vm_args += mx.get_runtime_jvm_args(names=dist_names, force_cp=force_cp)
    if force_cp:
        vm_args += ["com.oracle.truffle.sl.launcher.SLMain"]
    else:
        vm_args += ["--module", "org.graalvm.sl_launcher/com.oracle.truffle.sl.launcher.SLMain"]
    vm_args += [target_path]
    mx.log("Running {} {}".format(mx.exe_suffix('native-image'), " ".join(vm_args)))
    mx.run([native_image_path] + vm_args)
    return target_path

def _truffle_gate_runner(args, tasks):
    jdk = mx.get_jdk(tag=mx.DEFAULT_JDK_TAG)
    if jdk.javaCompliance < '9':
        with Task('Truffle Javadoc', tasks) as t:
            if t: javadoc([])
    with Task('File name length check', tasks) as t:
        if t: check_filename_length([])
    with Task('Truffle Signature Tests', tasks) as t:
        if t: sigtest(['--check', 'binary'])
    with Task('Truffle UnitTests', tasks) as t:
        if t: unittest(list(['--suite', 'truffle', '--enable-timing', '--verbose', '--max-class-failures=25']))
    if jdk.javaCompliance >= '21':
        with Task('Truffle NFI tests with Panama Backend', tasks) as t:
            if t:
                testPath = mx.distribution('TRUFFLE_TEST_NATIVE').output
                args = ['-Dnative.test.backend=panama', '-Dnative.test.path.panama=' + testPath]
                # testlibArg = mx_subst.path_substitutions.substitute('-Dnative.test.path.panama=<path:TRUFFLE_TEST_NATIVE>')
                if mx.project('com.oracle.truffle.nfi.backend.panama').javaPreviewNeeded:
                    args += ['--enable-preview']
                unittest(args + ['com.oracle.truffle.nfi.test', '--enable-timing', '--verbose'])
    with Task('TruffleString UnitTests without Java String Compaction', tasks) as t:
        if t: unittest(list(['-XX:-CompactStrings', '--suite', 'truffle', '--enable-timing', '--verbose', '--max-class-failures=25', 'com.oracle.truffle.api.strings.test']))
    if os.getenv('DISABLE_DSL_STATE_BITS_TESTS', 'false').lower() != 'true':
        with Task('Truffle DSL max state bit tests', tasks) as t:
            if t:
                _truffle_gate_state_bitwidth_tests()
    with Task('Validate parsers', tasks) as t:
        if t: validate_parsers()

# Run in vm suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -s "Truffle Unchained Truffle ModulePath Unit Tests"
def truffle_jvm_module_path_unit_tests_gate():
    unittest(list(['--suite', 'truffle', '--use-graalvm', '--enable-timing', '--verbose', '--max-class-failures=25']))

# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -s "Truffle Unchained Truffle ClassPath Unit Tests"
def truffle_jvm_class_path_unit_tests_gate():
    unittest(list(['--suite', 'truffle', '--use-graalvm', '--enable-timing', '--force-classpath', '--verbose', '--max-class-failures=25']))

    # GR-50223 temporarily still run tests with class-path isolation. they are to be removed.
    test_classes = [
            "com.oracle.truffle.api.test.polyglot",
            "com.oracle.truffle.api.test.examples",
            "com.oracle.truffle.tck.tests",
        ]
    unittest(list(['--suite', 'truffle', '-Dpolyglotimpl.DisableClassPathIsolation=false', '--use-graalvm', '--enable-timing', '--force-classpath', '--verbose', '--max-class-failures=25'] + test_classes))


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -s "Truffle Unchained SL JVM"
def sl_jvm_gate_tests():
    _sl_jvm_gate_tests(mx.get_jdk(tag='graalvm'), force_cp=False, supports_optimization=True)
    _sl_jvm_gate_tests(mx.get_jdk(tag='graalvm'), force_cp=True, supports_optimization=True)

    _sl_jvm_gate_tests(mx.get_jdk(tag='default'), force_cp=False, supports_optimization=False)
    _sl_jvm_gate_tests(mx.get_jdk(tag='default'), force_cp=True, supports_optimization=False)


def _sl_jvm_gate_tests(jdk, force_cp=False, supports_optimization=True):
    default_args = []
    if not supports_optimization:
        default_args += ['--engine.WarnInterpreterOnly=false']

    def run_jvm_fallback(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false'] + default_args, use_optimized_runtime=False, force_cp=force_cp)
    def run_jvm_optimized(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output'] + default_args, use_optimized_runtime=True, force_cp=force_cp)
    def run_jvm_optimized_immediately(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false'] + default_args, use_optimized_runtime=True, force_cp=force_cp)
    def run_jvmci_disabled(test_file):
        return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false', '-XX:-EnableJVMCI'] + default_args, use_optimized_runtime=True, force_cp=force_cp)

    mx.log(f'Run SL JVM Fallback Test on {jdk.home} force_cp={force_cp}')
    _run_sl_tests(run_jvm_fallback)
    mx.log(f'Run SL JVM Optimized Test on {jdk.home} force_cp={force_cp}')
    _run_sl_tests(run_jvm_optimized)
    mx.log(f'Run SL JVM JVMCI disabled on {jdk.home} force_cp={force_cp}')
    _run_sl_tests(run_jvmci_disabled)

    if supports_optimization:
        mx.log(f'Run SL JVM Optimized Immediately Test on {jdk.home} force_cp={force_cp}')
        _run_sl_tests(run_jvm_optimized_immediately)

    # test if the enterprise compiler is in use
    # that everything works fine if truffle-enterprise.jar is not available
    enterprise = _get_enterprise_truffle()
    if enterprise:
        def run_jvm_no_enterprise_optimized(test_file):
            return _sl_command(jdk, [], [test_file, '--disable-launcher-output'] + default_args, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)
        def run_jvm_no_enterprise_optimized_immediately(test_file):
            return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false'] + default_args, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)
        def run_jvm_no_enterprise_jvmci_disabled(test_file):
            return _sl_command(jdk, [], [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false', '-XX:-EnableJVMCI'] + default_args, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)

        mx.log(f'Run SL JVM Optimized  Test No Truffle Enterprise on {jdk.home} force_cp={force_cp}')
        _run_sl_tests(run_jvm_no_enterprise_optimized)

        if supports_optimization:
            mx.log(f'Run SL JVM Optimized Immediately Test No Truffle Enterprise on {jdk.home} force_cp={force_cp}')
            _run_sl_tests(run_jvm_no_enterprise_optimized_immediately)

        mx.log(f'Run SL JVM Optimized  Test No Truffle Enterprise JVMCI disabled on {jdk.home} force_cp={force_cp}')
        _run_sl_tests(run_jvm_no_enterprise_jvmci_disabled)


# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -s "Truffle Unchained SL Native Optimized"
def sl_native_optimized_gate_tests():
    _sl_native_optimized_gate_tests(force_cp=False)
    _sl_native_optimized_gate_tests(force_cp=True)

def _sl_native_optimized_gate_tests(force_cp):
    target_dir = tempfile.mkdtemp()
    jdk = mx.get_jdk(tag='graalvm')
    image = _native_image_sl(jdk, [], target_dir, use_optimized_runtime=True, use_enterprise=True)

    def run_native_optimized(test_file):
        return [image] + [test_file, '--disable-launcher-output']
    def run_native_optimized_immediately(test_file):
        return [image] + [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false']

    mx.log("Run SL Native Optimized Test")
    _run_sl_tests(run_native_optimized)
    mx.log("Run SL Native Optimized Immediately Test")
    _run_sl_tests(run_native_optimized_immediately)

    mx.rmtree(target_dir)

    # test if the enterprise compiler is in use
    # that everything works fine if truffle-enterprise.jar is not availble
    enterprise = _get_enterprise_truffle()
    if enterprise:
        target_dir = tempfile.mkdtemp()
        image = _native_image_sl(jdk, [], target_dir, use_optimized_runtime=True, use_enterprise=False, force_cp=force_cp)

        def run_no_enterprise_native_optimized(test_file):
            return [image] + [test_file, '--disable-launcher-output']
        def run_no_enterprise_native_optimized_immediately(test_file):
            return [image] + [test_file, '--disable-launcher-output', '--engine.CompileImmediately', '--engine.BackgroundCompilation=false']

        mx.log("Run SL Native Optimized Test No Truffle Enterprise")
        _run_sl_tests(run_no_enterprise_native_optimized)
        mx.log("Run SL Native Optimized Immediately Test No Truffle Enterprise")
        _run_sl_tests(run_no_enterprise_native_optimized_immediately)

        shutil.rmtree(target_dir)

# Run in VM suite with:
# mx --env ce --native-images=. build
# mx --env ce --native-images=. gate -o -s "Truffle Unchained SL Native Fallback"
def sl_native_fallback_gate_tests():
    _sl_native_fallback_gate_tests(force_cp=False)
    _sl_native_fallback_gate_tests(force_cp=True)

def _sl_native_fallback_gate_tests(force_cp):
    target_dir = tempfile.mkdtemp()
    jdk = mx.get_jdk(tag='graalvm')
    image = _native_image_sl(jdk, [], target_dir, use_optimized_runtime=False, force_cp=force_cp)

    def run_native_fallback(test_file):
        return [image] + [test_file, '--disable-launcher-output', '--engine.WarnInterpreterOnly=false']

    mx.log("Run SL Native Fallback Test")
    _run_sl_tests(run_native_fallback)

    mx.rmtree(target_dir)

def _run_sl_tests(create_command):
    sl_test = mx.project("com.oracle.truffle.sl.test")
    test_path = join(_suite.dir, sl_test.subDir, sl_test.name, "src", "tests")
    for f in os.listdir(test_path):
        if f.endswith('.sl'):
            base_name = os.path.splitext(f)[0]
            test_file = join(test_path, base_name + '.sl')
            expected_file = join(test_path, base_name + '.output')
            temp = tempfile.NamedTemporaryFile(delete=False)
            command = create_command(test_file)
            mx.log("Running SL test {}".format(test_file))
            with open(temp.name, 'w') as out:
                mx.run(command, nonZeroIsFatal=False, out=out, err=out)
                out.flush()
            diff = compare_files(expected_file, temp.name)
            if diff:
                mx.log("Failed command: {}".format(" ".join(command)))
                mx.abort("Output does not match expected output: {}".format(''.join(diff)))

            # delete file only on success
            os.unlink(temp.name)

def compare_files(file1_path, file2_path):
    with open(file1_path, 'r') as file1, open(file2_path, 'r') as file2:
        content1 = file1.readlines()
        content2 = file2.readlines()
        diff = difflib.unified_diff(content1, content2, fromfile=file1_path, tofile=file2_path)
        return list(diff)

# The Truffle DSL specialization state bit width computation is complicated and
# rarely used as the default maximum bit width of 32 is rarely exceeded. Therefore
# we rebuild the truffle tests with a number of max state bit width values to
# force using multiple state fields for the tests. This makes sure the tests
# do not break for rarely used combination of features and bit widths.
def _truffle_gate_state_bitwidth_tests():
    runs = [1, 2, 4, 8, 16]
    for run_bits in runs:
        build_args = ['-f', '-p', '--dependencies', 'TRUFFLE_TEST', '--force-javac',
                      '-A-Atruffle.dsl.StateBitWidth={0}'.format(run_bits)]

        unittest_args = ['--suite', 'truffle', '--enable-timing', '--max-class-failures=25', '-Dtruffle.dsl.StateBitWidth={0}'.format(run_bits),
                         'com.oracle.truffle.api.dsl.test', 'com.oracle.truffle.api.library.test', 'com.oracle.truffle.sl.test']
        try:
            mx.build(build_args)
            unittest(unittest_args)
        finally:
            mx.log('Completed Truffle DSL state bitwidth test. Reproduce with:')
            mx.log('  mx build {0}'.format(" ".join(build_args)))
            mx.log('  mx unittest {0}'.format(" ".join(unittest_args)))

mx_gate.add_gate_runner(_suite, _truffle_gate_runner)

mx.update_commands(_suite, {
    'javadoc' : [javadoc, '[SL args|@VM options]'],
    'sl' : [sl, '[SL args|@VM options]'],
    'slnative' : [slnative, '[SL args|@VM options]'],
})

def _is_graalvm(jdk):
    releaseFile = os.path.join(jdk.home, "release")
    if exists(releaseFile):
        with open(releaseFile) as f:
            pattern = re.compile('^GRAALVM_VERSION=*')
            for line in f.readlines():
                if pattern.match(line):
                    return True
    return False

def _collect_class_path_entries(cp_entries_filter, entries_collector, properties_collector):
    def import_visitor(suite, suite_import, predicate, collector, javaProperties, seenSuites, **extra_args):
        suite_collector(mx.suite(suite_import.name), predicate, collector, javaProperties, seenSuites)

    def suite_collector(suite, predicate, collector, javaProperties, seenSuites):
        if suite.name in seenSuites:
            return
        seenSuites.add(suite.name)
        suite.visit_imports(import_visitor, predicate=predicate, collector=collector, javaProperties=javaProperties, seenSuites=seenSuites)
        for dist in suite.dists:
            if predicate(dist):
                for distCpEntry in mx.classpath_entries(dist):
                    if hasattr(distCpEntry, "getJavaProperties"):
                        for key, value in distCpEntry.getJavaProperties().items():
                            javaProperties[key] = value
                    if distCpEntry.isJdkLibrary() or distCpEntry.isJreLibrary():
                        cpPath = distCpEntry.classpath_repr(mx.get_jdk(), resolve=True)
                    else:
                        cpPath = distCpEntry.classpath_repr(resolve=True)
                    if cpPath:
                        collector[cpPath] = None
    suite_collector(mx.primary_suite(), cp_entries_filter, entries_collector, properties_collector, set())

def _collect_class_path_entries_by_resource(requiredResources, entries_collector, properties_collector):
    """
    Collects class path for JAR distributions containing any resource from requiredResources.

    :param requiredResources: an iterable of resources. At least one of them has to exist to include the
            distribution class path entries.
    :param entries_collector: the list to add the class paths entries into.
    :properties_collector: the list to add the distribution Java properties into.
    """
    def has_resource(dist):
        if dist.isJARDistribution() and exists(dist.path):
            if isdir(dist.path):
                for requiredResource in requiredResources:
                    if exists(join(dist.path, requiredResource)):
                        return True
            else:
                with zipfile.ZipFile(dist.path, "r") as zf:
                    for requiredResource in requiredResources:
                        try:
                            zf.getinfo(requiredResource)
                        except KeyError:
                            pass
                        else:
                            return True
                    return False
        else:
            return False
    _collect_class_path_entries(has_resource, entries_collector, properties_collector)


def _collect_class_path_entries_by_module_descriptor(required_services, entries_collector, properties_collector):
    """
    Collects class path for JAR distributions providing any service from requiredServices.

    :param required_services: an iterable of service fully qualified names. At least one of them has to exist to include
            distribution class path entries.
    :param entries_collector: the list to add the class paths entries into.
    :param properties_collector: the list to add the distribution Java properties into.
    """
    required_set = set(required_services)

    def provides_service(dist):
        if dist.isJARDistribution() and exists(dist.path):
            module_name = get_module_name(dist)
            if module_name:
                jmd = as_java_module(dist, mx.get_jdk())
                return len(required_set.intersection(jmd.provides.keys())) != 0
        return False
    _collect_class_path_entries(provides_service, entries_collector, properties_collector)

def _collect_class_path_entries_by_name(distributionName, entries_collector, properties_collector):
    cp_filter = lambda dist: dist.isJARDistribution() and  dist.name == distributionName and exists(dist.path)
    _collect_class_path_entries(cp_filter, entries_collector, properties_collector)

def _collect_languages(entries_collector, properties_collector):
    _collect_class_path_entries_by_module_descriptor([
        "com.oracle.truffle.api.provider.TruffleLanguageProvider"],
        entries_collector, properties_collector)
    _collect_class_path_entries_by_resource([
        # GR-46292 Remove the deprecated TruffleLanguage.Provider
        "META-INF/truffle/language",
        # GR-46292 Remove the deprecated TruffleLanguage.Provider
        "META-INF/services/com.oracle.truffle.api.TruffleLanguage$Provider",
        # Not all languages are already modularized. For non-modularized languages we require
        # a registration of the TruffleLanguageProvider in the META-INF/services
        "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider"],
        entries_collector, properties_collector)

def _collect_tck_providers(entries_collector, properties_collector):
    _collect_class_path_entries_by_resource(["META-INF/services/org.graalvm.polyglot.tck.LanguageProvider"], entries_collector, properties_collector)

def _unittest_config_participant_tck(config):

    def find_path_arg(vmArgs, prefix):
        for index in reversed(range(len(vmArgs) - 1)):
            if prefix in vmArgs[index]:
                return index, vmArgs[index][len(prefix):]
        return None, None

    javaPropertiesToAdd = OrderedDict()
    providers = OrderedDict()
    _collect_tck_providers(providers, javaPropertiesToAdd)
    languages = OrderedDict()
    _collect_languages(languages, javaPropertiesToAdd)
    _collect_class_path_entries_by_name("TRUFFLE_TCK_INSTRUMENTATION", languages, javaPropertiesToAdd)
    vmArgs, mainClass, mainClassArgs = config
    cpIndex, cpValue = mx.find_classpath_arg(vmArgs)
    cpBuilder = OrderedDict()
    if cpValue:
        for cpElement in cpValue.split(os.pathsep):
            cpBuilder[cpElement] = None
    for providerCpElement in providers:
        cpBuilder[providerCpElement] = None

    if _is_graalvm(mx.get_jdk()):
        boot_cp = OrderedDict()
        _collect_class_path_entries_by_name("TRUFFLE_TCK_COMMON", boot_cp, javaPropertiesToAdd)
        tpIndex, tpValue = find_path_arg(vmArgs, '-Dtruffle.class.path.append=')
        tpBuilder = OrderedDict()
        if tpValue:
            for cpElement in tpValue.split(os.pathsep):
                tpBuilder[cpElement] = None
        for langCpElement in languages:
            tpBuilder[langCpElement] = None
        bpIndex, bpValue = find_path_arg(vmArgs, '-Xbootclasspath/a:')
        bpBuilder = OrderedDict()
        if bpValue:
            for cpElement in bpValue.split(os.pathsep):
                bpBuilder[cpElement] = None
        for bootCpElement in boot_cp:
            bpBuilder[bootCpElement] = None
            cpBuilder.pop(bootCpElement, None)
            tpBuilder.pop(bootCpElement, None)
        tpValue = '-Dtruffle.class.path.append=' + os.pathsep.join((e for e in tpBuilder))
        if tpIndex:
            vmArgs[tpIndex] = tpValue
        else:
            vmArgs.append(tpValue)
        bpValue = '-Xbootclasspath/a:' + os.pathsep.join((e for e in bpBuilder))
        if bpIndex:
            vmArgs[bpIndex] = bpValue
        else:
            vmArgs.append(bpValue)
    else:
        for langCpElement in languages:
            cpBuilder[langCpElement] = None
    cpValue = os.pathsep.join((e for e in cpBuilder))
    if cpIndex:
        vmArgs[cpIndex] = cpValue
    else:
        vmArgs.append("-cp")
        vmArgs.append(cpValue)
    for key, value in javaPropertiesToAdd.items():
        vmArgs.append("-D" + key + "=" + value)
    return (vmArgs, mainClass, mainClassArgs)

_shouldRunTCKParticipant = True

def should_add_tck_participant(shouldInstal):
    global _shouldRunTCKParticipant
    _shouldRunTCKParticipant = shouldInstal

"""
Merges META-INF/truffle/language and META-INF/truffle/instrument files.
This code is tightly coupled with the file format generated by
LanguageRegistrationProcessor and InstrumentRegistrationProcessor.
"""
class TruffleArchiveParticipant:
    providersRE = re.compile(r'(?:META-INF/versions/([1-9][0-9]*)/)?META-INF/truffle-registrations/(.+)')

    def __opened__(self, arc, srcArc, services):
        self.services = services
        self.arc = arc

    def __process__(self, arcname, contents_supplier, is_source):
        if is_source:
            return False
        m = TruffleArchiveParticipant.providersRE.match(arcname)
        if m:
            provider = m.group(2)
            for service in contents_supplier().decode().strip().split(os.linesep):
                assert service
                version = m.group(1)
                if version is None:
                    # Non-versioned service
                    self.services.setdefault(service, []).append(provider)
                else:
                    # Versioned service
                    services = self.services.setdefault(int(version), {})
                    services.setdefault(service, []).append(provider)
            return True
        return False

def mx_post_parse_cmd_line(opts):

    def _uses_truffle_dsl_processor(dist):
        for dep in dist.deps:
            if dep.name.startswith('TRUFFLE_DSL_PROCESSOR'):
                return True
        truffle_dsl_processors = set()
        def visit(dep, edge):
            if dep is not dist and dep.isJavaProject():
                for ap in dep.annotation_processors():
                    if ap.name.startswith('TRUFFLE_DSL_PROCESSOR'):
                        truffle_dsl_processors.add(ap)
        dist.walk_deps(visit=visit)
        return len(truffle_dsl_processors) != 0

    for d in mx.dependencies():
        if d.isJARDistribution():
            if _uses_truffle_dsl_processor(d):
                d.set_archiveparticipant(TruffleArchiveParticipant())

_tckHelpSuffix = """
    TCK options:

      --tck-configuration                  configuration {compiler|debugger|default}
          compile                          executes TCK tests with immediate comilation
          debugger                         executes TCK tests with enabled debugalot instrument
          default                          executes TCK tests
"""

def _execute_debugger_test(testFilter, logFile, testEvaluation=False, unitTestOptions=None, jvmOptions=None):
    """
    Executes given unit tests with enabled debugalot instrument.
    The 'testFilter' argument is a filter unit test pattern.
    The 'logFile' argument is a file path to store the instrument output into.
    The 'testEvaluation' argument enables evaluation testing, default is False.
    The 'unitTestOptions' argument is a list of unit test options.
    The 'jvmOptions' argument is a list of VM options.
    """
    debugalot_options = ["-Dpolyglot.debugalot=true"]
    if testEvaluation:
        debugalot_options.append("-Dpolyglot.debugalot.Eval=true")
    debugalot_options.append("-Dpolyglot.debugalot.LogFile=" + logFile)
    args = []
    if unitTestOptions is not None:
        args = args + unitTestOptions
    args = args + ["--"]
    if jvmOptions is not None:
        args = args + jvmOptions
    args = args + debugalot_options
    args = args + testFilter
    unittest(args)

def execute_tck(graalvm_home, mode='default', language_filter=None, values_filter=None, tests_filter=None, vm_args=None):
    """
    Executes Truffle TCK with all TCK providers reachable from the primary suite and all languages installed in the given GraalVM.

    :param graalvm_home: a path to GraalVM
    :param mode: a name of TCK mode,
        'default' - executes the test with default GraalVM configuration,
        'compile' - compiles the tests before execution
    :param language_filter: the language id, limits TCK tests to certain language
    :param values_filter: an iterable of value constructors language ids, limits TCK values to certain language(s)
    :param tests_filter: a substring of TCK test name or an iterable of substrings of TCK test names
    :param vm_args: iterable containing additional Java VM args
    """
    cp = OrderedDict()
    _collect_tck_providers(cp, dict())
    truffle_cp = OrderedDict()
    _collect_class_path_entries_by_name("TRUFFLE_TCK_INSTRUMENTATION", truffle_cp, dict())
    _collect_class_path_entries_by_name("TRUFFLE_SL", truffle_cp, dict())
    boot_cp = OrderedDict()
    _collect_class_path_entries_by_name("TRUFFLE_TCK_COMMON", boot_cp, dict())
    return tck.execute_tck(graalvm_home, mode=tck.Mode.for_name(mode), language_filter=language_filter, values_filter=values_filter,
        tests_filter=tests_filter, cp=cp.keys(), truffle_cp=truffle_cp.keys(), boot_cp=boot_cp, vm_args=vm_args)


def _tck(args):
    """runs TCK tests"""

    parser = ArgumentParser(prog="mx tck", description="run the TCK tests", formatter_class=RawDescriptionHelpFormatter, epilog=_tckHelpSuffix)
    parser.add_argument("--tck-configuration", help="TCK configuration", choices=["compile", "debugger", "default"], default="default")
    parsed_args, args = parser.parse_known_args(args)
    tckConfiguration = parsed_args.tck_configuration
    index = len(args)
    for arg in reversed(args):
        if arg.startswith("-"):
            break
        index = index - 1
    args_no_tests = args[0:index]
    tests = args[index:len(args)]
    if len(tests) == 0:
        tests = ["com.oracle.truffle.tck.tests"]
    index = len(args_no_tests)
    has_separator_arg = False
    for arg in reversed(args_no_tests):
        if arg.startswith("--"):
            if arg == "--":
                has_separator_arg = True
            break
        index = index - 1
    unitTestOptions = args_no_tests[0:max(index - (1 if has_separator_arg else 0), 0)]
    jvmOptions = args_no_tests[index:len(args_no_tests)]
    if tckConfiguration == "default":
        unittest(unitTestOptions + ["--"] + jvmOptions + tests)
    elif tckConfiguration == "debugger":
        with mx.SafeFileCreation(os.path.join(tempfile.gettempdir(), "debugalot")) as sfc:
            _execute_debugger_test(tests, sfc.tmpPath, False, unitTestOptions, jvmOptions)
    elif tckConfiguration == "compile":
        if not _is_graalvm(mx.get_jdk()):
            mx.abort("The 'compile' TCK configuration requires graalvm execution, run with --java-home=<path_to_graalvm>.")
        compileOptions = [
            "-Dpolyglot.engine.AllowExperimentalOptions=true",
            "-Dpolyglot.engine.Mode=latency",
            "-Dpolyglot.engine.CompilationFailureAction=Throw",
            "-Dpolyglot.engine.CompileImmediately=true",
            "-Dpolyglot.engine.BackgroundCompilation=false",
        ]
        unittest(unitTestOptions + ["--"] + jvmOptions + compileOptions + tests)


mx.update_commands(_suite, {
    'tck': [_tck, "[--tck-configuration {compile|debugger|default}] [unittest options] [--] [VM options] [filters...]", _tckHelpSuffix]
})


def check_filename_length(args):
    """check that all file name lengths are short enough for eCryptfs"""
    # For eCryptfs, see https://bugs.launchpad.net/ecryptfs/+bug/344878
    parser = ArgumentParser(prog="mx check-filename-length", description="Check file name length")
    parser.parse_known_args(args)
    max_length = 143
    too_long = []
    for _, _, filenames in os.walk('.'):
        for filename in filenames:
            if len(filename) > max_length:
                too_long.append(filename)
    if too_long:
        mx.log_error("The following file names are too long for eCryptfs: ")
        for x in too_long:
            mx.log_error(x)
        mx.abort("File names that are too long where found. Ensure all file names are under %d characters long." % max_length)

def create_dsl_parser(args=None, out=None):
    """create the DSL expression parser using antlr"""
    create_parser("com.oracle.truffle.dsl.processor", "com.oracle.truffle.dsl.processor.expression", "Expression", args=args, out=out)

def create_sl_parser(args=None, out=None):
    """create the SimpleLanguage parser using antlr"""
    create_parser("com.oracle.truffle.sl", "com.oracle.truffle.sl.parser", "SimpleLanguage", None, args, out)

def create_parser(grammar_project, grammar_package, grammar_name, copyright_template=None, args=None, out=None, postprocess=None, shaded=False):
    """create the DSL expression parser using antlr"""
    grammar_dir = os.path.join(mx.project(grammar_project).source_dirs()[0], *grammar_package.split(".")) + os.path.sep
    g4_filename = grammar_dir + grammar_name + ".g4"
    mx.run_java(mx.get_runtime_jvm_args(['ANTLR4_COMPLETE']) + ["org.antlr.v4.Tool", "-package", grammar_package, "-no-listener"] + args + [g4_filename], out=out)

    if copyright_template is None:
        # extract copyright header from .g4 file
        copyright_header = ''
        with open(g4_filename) as g:
            for line in g:
                copyright_header += line
                if line == ' */\n':
                    break
        assert copyright_header.startswith('/*\n * Copyright (c)') and copyright_header.endswith(' */\n'), copyright_header
        copyright_header += '// Checkstyle: stop\n'
        copyright_header += '//@formatter:off\n'
        copyright_template = copyright_header + '{0}\n'

    for filename in [grammar_dir + grammar_name + "Lexer.java", grammar_dir + grammar_name + "Parser.java"]:
        with open(filename, 'r') as content_file:
            content = content_file.read()
        # remove first line
        content = "\n".join(content.split("\n")[1:])
        # modify SuppressWarnings to remove useless entries
        content = re.compile("^@SuppressWarnings.*$", re.MULTILINE).sub('@SuppressWarnings({"all", "this-escape"})', content)
        # remove useless casts
        content = re.compile(r"\(\([a-zA-Z_]*Context\)_localctx\)").sub('_localctx', content)
        content = re.compile(r"\(Token\)_errHandler.recoverInline\(this\)").sub('_errHandler.recoverInline(this)', content)
        # add copyright header
        content = copyright_template.format(content)
        if shaded:
            # replace qualified class names with shadowed package names
            content = re.compile(r"\b(org\.antlr\.v4\.runtime(?:\.\w+)+)\b").sub(r'org.graalvm.shadowed.\1', content)
            # replace imports with shadowed package names
            content = re.compile(r"^import (org\.antlr\.v4\.runtime(?:\.\w+)*(?:\.\*)?);", re.MULTILINE).sub(r'import org.graalvm.shadowed.\1;', content)
        # user provided post-processing hook:
        if postprocess is not None:
            content = postprocess(content)
        with open(filename, 'w') as content_file:
            content_file.write(content)

def validate_parsers(args=None, out=None):
    validate_parser("com.oracle.truffle.sl", "com/oracle/truffle/sl/parser/SimpleLanguage.g4", create_sl_parser)
    validate_parser("com.oracle.truffle.dsl.processor", "com/oracle/truffle/dsl/processor/expression/Expression.g4", create_dsl_parser)

def validate_parser(grammar_project, grammar_path, create_command, args=None, out=None):
    def read_file(path):
        with open(path, "r") as f:
            return f.readlines()
    parser_path = grammar_path.replace(".g4", "Parser.java")
    lexer_path = grammar_path.replace(".g4", "Lexer.java")
    parser = abspath(mx.project(grammar_project).source_dirs()[0] + "/" + parser_path)
    lexer = abspath(mx.project(grammar_project).source_dirs()[0] + "/" + lexer_path)
    parser_before = read_file(parser)
    lexer_before = read_file(lexer)
    create_command([], out)
    parser_after = read_file(parser)
    lexer_after = read_file(lexer)
    for before, after, path in ((parser_before, parser_after, parser), (lexer_before, lexer_after, lexer)):
        if before != after:
            diff = ''.join(difflib.unified_diff(before, after))
            nl = os.linesep
            mx.abort(f"Content generated from {grammar_path} does not match content of {path}:{nl}" +
                    f"{diff}{nl}" +
                    "Make sure the grammar files are up to date with the generated code. You can regenerate the generated code using mx.")


def register_polyglot_isolate_distributions(register_distribution, language_id, language_distribution, isolate_library_layout_distribution, internal_resource_project):
    """
    Registers the polyglot isolate resource distribution and isolate resource meta-POM distribution.
    The created polyglot isolate resource distribution is named `<ID>_ISOLATE_RESOURCES`, inheriting the Maven group ID
    from the given `language_distribution`, and the Maven artifact ID is `<id>-isolate`.
    The meta-POM distribution is named `<ID>_ISOLATE`, having the Maven group ID `org.graalvm.polyglot`,
    and the Maven artifact ID is `<id>-isolate`.

    :param register_distribution: A callback to dynamically register the distribution, obtained as a parameter from `mx_register_dynamic_suite_constituents`.
    :type register_distribution: (mx.Distribution) -> None
    :param language_id: The language ID.
    :param language_distribution: The language distribution used to inherit distribution properties.
    :param isolate_library_layout_distribution: The layout distribution with polyglot isolate library.
    :param internal_resource_project: The internal resource project used for unpacking the polyglot isolate library.
    """
    assert language_distribution
    assert isolate_library_layout_distribution
    assert internal_resource_project
    owner_suite = language_distribution.suite
    resources_dist_name = f'{language_id.upper()}_ISOLATE_RESOURCES'
    isolate_dist_name = f'{language_id.upper()}_ISOLATE'
    layout_dist_qualified_name = f'{isolate_library_layout_distribution.suite.name}:{isolate_library_layout_distribution.name}'
    maven_group_id = language_distribution.maven_group_id()
    maven_artifact_id = f'{language_id}-isolate'
    module_name = f'{get_module_name(language_distribution)}.isolate'
    licenses = set()
    licenses.update(language_distribution.theLicense)
    licenses.update(owner_suite.defaultLicense)
    attrs = {
        'description': f'Polyglot isolate resources for {language_id}.',
        'moduleInfo': {
            'name': module_name,
        },
        'maven': {
            'groupId': maven_group_id,
            'artifactId': maven_artifact_id,
            'tag': ['default', 'public'],
        },
        'mavenNoJavadoc': True,
        'mavenNoSources': True,
    }
    isolate_library_dist = mx_jardistribution.JARDistribution(
        suite=owner_suite,
        name=resources_dist_name,
        subDir=language_distribution.subDir,
        path=None,
        sourcesPath=None,
        deps=[
            internal_resource_project.name,
            layout_dist_qualified_name,
        ],
        mainClass=None,
        excludedLibs=[],
        distDependencies=['truffle:TRUFFLE_API'],
        javaCompliance=str(internal_resource_project.javaCompliance)+'+',
        platformDependent=True,
        theLicense=list(licenses),
        compress=True,
        **attrs
    )
    register_distribution(isolate_library_dist)
    attrs = {
        'description': f'The {language_id} polyglot isolate.',
        'maven': {
            'groupId': 'org.graalvm.polyglot',
            'artifactId': maven_artifact_id,
            'tag': ['default', 'public'],
        },
    }
    # The graal-enterprise suite may not be fully loaded.
    # We cannot look up the TRUFFLE_ENTERPRISE distribution to resolve its license
    # We pass directly the license id
    licenses.update(['GFTC'])
    meta_pom_dist = mx_pomdistribution.POMDistribution(
        suite=owner_suite,
        name=isolate_dist_name,
        distDependencies=[],
        runtimeDependencies=[
            resources_dist_name,
            'graal-enterprise:TRUFFLE_ENTERPRISE',
        ],
        theLicense=sorted(list(licenses)),
        **attrs)
    register_distribution(meta_pom_dist)

class LibffiBuilderProject(mx.AbstractNativeProject, mx_native.NativeDependency):  # pylint: disable=too-many-ancestors
    """Project for building libffi from source.

    The build is performed by:
        1. Extracting the sources,
        2. Applying the platform dependent patches, and
        3. Invoking the platform dependent builder that we delegate to.
    """

    def __init__(self, suite, name, deps, workingSets, **kwargs):
        subDir = 'src'
        srcDirs = ['patches']
        d = mx.join(suite.dir, subDir, name)
        super(LibffiBuilderProject, self).__init__(suite, name, subDir, srcDirs, deps, workingSets, d, **kwargs)

        self.out_dir = self.get_output_root()
        if mx.get_os() == 'windows':
            self.delegate = mx_native.DefaultNativeProject(suite, name, subDir, [], [], None,
                                                           mx.join(self.out_dir, 'libffi-3.4.6'),
                                                           'static_lib',
                                                           deliverable='ffi',
                                                           cflags=['-MD', '-O2', '-DFFI_STATIC_BUILD'])
            self.delegate._source = dict(tree=['include',
                                               'src',
                                               mx.join('src', 'x86')],
                                         files={'.h': [mx.join('include', 'ffi.h'),
                                                       mx.join('include', 'ffitarget.h'),
                                                       mx.join('src', 'fficonfig.h'),
                                                       mx.join('src', 'ffi_common.h')],
                                                '.c': [mx.join('src', 'closures.c'),
                                                       mx.join('src', 'prep_cif.c'),
                                                       mx.join('src', 'raw_api.c'),
                                                       mx.join('src', 'types.c'),
                                                       mx.join('src', 'tramp.c'),
                                                       mx.join('src', 'x86', 'ffiw64.c')],
                                                '.S': [mx.join('src', 'x86', 'win64_intel.S')]})
        else:
            class LibtoolNativeProject(mx.NativeProject,  # pylint: disable=too-many-ancestors
                                       mx_native.NativeDependency):
                include_dirs = property(lambda self: [mx.join(self.getOutput(), 'include')])
                libs = property(lambda self: [next(self.getArchivableResults(single=True))[0]])

                def getArchivableResults(self, use_relpath=True, single=False):
                    for file_path, archive_path in super(LibtoolNativeProject, self).getArchivableResults(use_relpath):
                        path_in_lt_objdir = mx.basename(mx.dirname(file_path)) == '.libs'
                        yield file_path, mx.basename(archive_path) if path_in_lt_objdir else archive_path
                        if single:
                            assert path_in_lt_objdir, 'the first build result must be from LT_OBJDIR'
                            break

            self.delegate = LibtoolNativeProject(suite, name, subDir, [], [], None,
                                                 ['.libs/libffi.a',
                                                  'include/ffi.h',
                                                  'include/ffitarget.h'],
                                                 mx.join(self.out_dir, 'libffi-build'),
                                                 mx.join(self.out_dir, 'libffi-3.4.6'))
            configure_args = ['--disable-dependency-tracking',
                              '--disable-shared',
                              '--with-pic',
                              ' CFLAGS="{}"'.format(' '.join(['-g', '-O3'] + (['-m64'] if mx.get_os() == 'solaris' else []))),
                              'CPPFLAGS="-DNO_JAVA_RAW_API"',
                             ]

            self.delegate.buildEnv = dict(
                SOURCES=mx.basename(self.delegate.dir),
                OUTPUT=mx.basename(self.delegate.getOutput()),
                CONFIGURE_ARGS=' '.join(configure_args)
            )

        self.include_dirs = self.delegate.include_dirs
        self.libs = self.delegate.libs

    def resolveDeps(self):
        super(LibffiBuilderProject, self).resolveDeps()
        self.delegate.resolveDeps()
        self.buildDependencies += self.delegate.buildDependencies

    @property
    def sources(self):
        assert len(self.deps) == 1, '{} must depend only on its sources'.format(self.name)
        return self.deps[0]

    @property
    def patches(self):
        """A list of patches that will be applied during a build."""
        def patch_dir(d):
            return mx.join(self.source_dirs()[0], d)

        def get_patches(patchdir):
            if os.path.isdir(patchdir):
                for patch in os.listdir(patchdir):
                    yield mx.join(patchdir, patch)

        for p in get_patches(patch_dir('common')):
            yield p
        os_arch_dir = patch_dir('{}-{}'.format(mx.get_os(), mx.get_arch()))
        if mx.exists(os_arch_dir):
            for p in get_patches(os_arch_dir):
                yield p
        else:
            for p in get_patches(patch_dir('others')):
                yield p

    def getBuildTask(self, args):
        return LibffiBuildTask(args, self)

    def getArchivableResults(self, use_relpath=True, single=False):
        return self.delegate.getArchivableResults(use_relpath, single)


class LibffiBuildTask(mx.AbstractNativeBuildTask):
    def __init__(self, args, project):
        super(LibffiBuildTask, self).__init__(args, project)
        self.delegate = project.delegate.getBuildTask(args)

    def __str__(self):
        return 'Building {}'.format(self.subject.name)

    def needsBuild(self, newestInput):
        is_needed, reason = super(LibffiBuildTask, self).needsBuild(newestInput)
        if is_needed:
            return True, reason

        output = self.newestOutput()
        newest_patch = mx.TimeStampFile.newest(self.subject.patches)
        if newest_patch and output.isOlderThan(newest_patch):
            return True, '{} is older than {}'.format(output, newest_patch)

        return False, 'all files are up to date'

    def newestOutput(self):
        output = self.delegate.newestOutput()
        return None if output and not output.exists() else output

    def build(self):
        assert not mx.exists(self.subject.out_dir), '{} must be cleaned before build'.format(self.subject.name)

        mx.log('Extracting {}...'.format(self.subject.sources))
        mx.Extractor.create(self.subject.sources.get_path(False)).extract(self.subject.out_dir)

        mx.log('Applying patches...')
        git_apply = ['git', 'apply', '--whitespace=nowarn', '--unsafe-paths', '--directory',
                     os.path.realpath(self.subject.delegate.dir)]
        for patch in self.subject.patches:
            mx.run(git_apply + [patch], cwd=self.subject.suite.vc_dir)

        self.delegate.logBuild()
        self.delegate.build()

    def clean(self, forBuild=False):
        mx.rmtree(self.subject.out_dir, ignore_errors=True)



mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle API',
    short_name='tfla',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Graal SDK', 'Truffle Compiler'],
    jar_distributions=[],
    jvmci_parent_jars=[
        'sdk:JNIUTILS',
        'truffle:TRUFFLE_API',
        'truffle:TRUFFLE_RUNTIME',
    ],
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle',
    short_name='tfl',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[
        'Truffle API',
        'GraalVM Launcher Common',
    ],
    jar_distributions=[],
    jvmci_parent_jars=[
        'truffle:LOCATOR',
    ],
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle Compiler',
    short_name='tflc',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=[],
    jvmci_parent_jars=[
        'truffle:TRUFFLE_COMPILER',
    ],
    stability="supported",
))

# Typically not included in releases
mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmTruffleLibrary(
    suite=_suite,
    name='Truffle DSL Processor',
    short_name='tflp',
    dir_name='truffle',
    license_files=[],
    third_party_license_files=[],
    dependencies=[],
    jar_distributions=['truffle:TRUFFLE_DSL_PROCESSOR'],
    jvmci_parent_jars=[],
    stability="supported",
))

truffle_nfi_component = mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle NFI',
    short_name='nfi',
    dir_name='nfi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:TRUFFLE_NFI'],
    support_distributions=['truffle:TRUFFLE_NFI_GRAALVM_SUPPORT'],
    support_libraries_distributions=['truffle:TRUFFLE_NFI_NATIVE_GRAALVM_SUPPORT'],
    installable=False,
    stability="supported",
)
mx_sdk_vm.register_graalvm_component(truffle_nfi_component)

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle NFI LIBFFI',
    short_name='nfi-libffi',
    dir_name='nfi',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle NFI'],
    truffle_jars=['truffle:TRUFFLE_NFI_LIBFFI', 'truffle:TRUFFLE_NFI_PANAMA'],
    installable=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='ICU4J',
    short_name='icu4j',
    dir_name='icu4j',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=[
        'truffle:TRUFFLE_ICU4J',
    ],
    support_distributions=['truffle:TRUFFLE_ICU4J_GRAALVM_SUPPORT'],
    installable=True,
    standalone=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='ANTLR4',
    short_name='antlr4',
    dir_name='antlr4',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:ANTLR4', 'truffle:TRUFFLE_ANTLR4'],
    support_distributions=['truffle:TRUFFLE_ANTLR4_GRAALVM_SUPPORT'],
    installable=True,
    standalone=False,
    stability="supported",
))

mx_sdk_vm.register_graalvm_component(mx_sdk_vm.GraalVmLanguage(
    suite=_suite,
    name='Truffle JSON Library',
    short_name='truffle-json',
    dir_name='truffle-json',
    license_files=[],
    third_party_license_files=[],
    dependencies=['Truffle'],
    truffle_jars=['truffle:TruffleJSON',
        'truffle:TRUFFLE_JSON',
    ],
    support_distributions=['truffle:TRUFFLE_JSON_GRAALVM_SUPPORT'],
    installable=False,
    standalone=False,
    stability="supported",
))


mx.update_commands(_suite, {
    'check-filename-length' : [check_filename_length, ""],
    'create-dsl-parser' : [create_dsl_parser, "create the DSL expression parser using antlr"],
    'create-sl-parser' : [create_sl_parser, "create the SimpleLanguage parser using antlr"],
})

mx_gate.add_jacoco_includes(['org.graalvm.*', 'com.oracle.truffle.*'])
