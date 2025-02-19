[![GraalVM](.github/assets/logo_320x64.svg)][website]

[![GraalVM Gate][badge-gate]][gate] [![GraalVM docs][badge-docs]][docs] [![GraalVM on Slack][badge-slack]][slack] [![GraalVM on Twitter][badge-twitter]][twitter] [![GraalVM on YouTube][badge-yt]][youtube]  [![License][badge-license]](#license)

Welcome to the **maintenance repository** of GraalVM Community Edition for JDK 21. This is a **source-only** repository maintained by the GraalVM community.

GraalVM is a high-performance JDK distribution designed to accelerate the execution of applications written in Java and other JVM languages along with support for JavaScript, Ruby, Python, and a number of other popular languages. To get the latest GraalVM version visit  the [GraalVM website][downloads].

Please refer to:

1. [BUILDING.md](BUILDING.md) for instructions on how to build GraalVM Community Edition for JDK 21.
2. [CONTRIBUTING.md](CONTRIBUTING.md) for information on how to contribute to this repository.
3. [MAINTAINING.md](MAINTAINING.md) for information on how to maintain this repository.

The project website at [https://www.graalvm.org/][website] describes how to [get started][getting-started], how to [stay connected][community], and how to [contribute][contributors].

## Documentation

Please refer to the [GraalVM website for documentation][docs]. You can find most of the documentation sources in the [_docs/_](docs/) directory in the same hierarchy as displayed on the website. Additional documentation including developer instructions for individual components can be found in corresponding _docs/_ sub-directories. The documentation for the Truffle framework, for example, is in [_truffle/docs/_](truffle/docs/).

## Get Support

* Open a [GitHub issue][issues] for backport requests or bug reports.
* Join the `#community-lts` channel in the [GraalVM Slack workspace][slack] to connect with the community.
* Report a security vulnerability according to the [Reporting Vulnerabilities guide][reporting-vulnerabilities].

## Repository Structure

This source repository is the main repository for GraalVM and includes the following components:

Directory | Description
------------ | -------------
[`.devcontainer/`](.devcontainer/) | Configuration files for GitHub dev containers.
[`.github/`](.github/) | Configuration files for GitHub issues, workflows, ….
[`compiler/`](compiler/) | [Graal compiler][reference-compiler], a modern, versatile compiler written in Java.
[`espresso/`](espresso/) | [Espresso][java-on-truffle], a meta-circular Java bytecode interpreter for the GraalVM.
[`java-benchmarks/`](java-benchmarks/) | Java benchmarks.
[`regex/`](regex/) | TRegex, a regular expression engine for other GraalVM languages.
[`sdk/`](sdk/) | [GraalVM SDK][graalvm-sdk], long-term supported APIs of GraalVM.
[`substratevm/`](substratevm/) | Framework for ahead-of-time (AOT) compilation with [Native Image][native-image].
[`sulong/`](sulong/) | [Sulong][reference-sulong], an engine for running LLVM bitcode on GraalVM.
[`tools/`](tools/) | Tools for GraalVM languages implemented with the instrumentation framework.
[`truffle/`](truffle/) | GraalVM's [language implementation framework][truffle] for creating languages and tools.
[`visualizer/`](visualizer/) | [Ideal Graph Visualizer (IGV)][igv], a tool for analyzing Graal compiler graphs.
[`vm/`](vm/) | Components for building GraalVM distributions.
[`wasm/`](wasm/) | [GraalWasm][reference-graalwasm], an engine for running WebAssembly programs on GraalVM.

## License

GraalVM Community Edition is open source and distributed under [version 2 of the GNU General Public License with the “Classpath” Exception](LICENSE), which are the same terms as for Java. The licenses of the individual GraalVM components are generally derivative of the license of a particular language (see the table below).

Component(s) | License
------------ | -------------
[Espresso](espresso/LICENSE), [Ideal Graph Visualizer](visualizer/LICENSE) | GPL 2
[GraalVM Compiler](compiler/LICENSE.md), [SubstrateVM](substratevm/LICENSE), [Tools](tools/LICENSE), [VM](vm/LICENSE_GRAALVM_CE) | GPL 2 with Classpath Exception
[GraalVM SDK](sdk/LICENSE.md), [GraalWasm](wasm/LICENSE), [Truffle Framework](truffle/LICENSE.md), [TRegex](regex/LICENSE.md) | Universal Permissive License
[Sulong](sulong/LICENSE) | 3-clause BSD


[badge-docs]: https://img.shields.io/badge/docs-read-green
[badge-gate]: https://github.com/graalvm/graalvm-community-jdk21u/actions/workflows/main.yml/badge.svg
[badge-license]: https://img.shields.io/badge/license-GPLv2+CE-green
[badge-slack]: https://img.shields.io/badge/Slack-join-active?logo=slack
[badge-twitter]: https://img.shields.io/badge/Twitter-@graalvm-active?logo=twitter
[badge-yt]: https://img.shields.io/badge/YouTube-subscribe-active?logo=youtube
[community]: https://www.graalvm.org/community/
[contributors]: https://www.graalvm.org/community/contributors/
[docs]: https://www.graalvm.org/jdk21/docs/
[downloads]: https://www.graalvm.org/downloads/
[gate]: https://github.com/graalvm/graalvm-community-jdk21u/actions/workflows/main.yml
[getting-started]: https://www.graalvm.org/jdk21/docs/getting-started/
[graalvm-demos]: https://github.com/graalvm/graalvm-demos
[graalvm-sdk]: https://www.graalvm.org/sdk/javadoc/
[igv]: https://www.graalvm.org/jdk21/tools/igv/
[issues]: https://github.com/graalvm/graalvm-community-jdk21u/issues
[java-on-truffle]: https://www.graalvm.org/jdk21/reference-manual/java-on-truffle/
[native-build-tools]: https://github.com/graalvm/native-build-tools
[native-image]: https://www.graalvm.org/native-image/
[reference-compiler]: https://www.graalvm.org/jdk21/reference-manual/java/compiler/
[reference-graalwasm]: https://www.graalvm.org/jdk21/reference-manual/wasm/
[reference-sulong]: https://www.graalvm.org/jdk21/reference-manual/llvm/
[reporting-vulnerabilities]: https://www.oracle.com/corporate/security-practices/assurance/vulnerability/reporting.html
[slack]: https://www.graalvm.org/slack-invitation/
[truffle]: https://www.graalvm.org/graalvm-as-a-platform/language-implementation-framework/
[twitter]: https://twitter.com/graalvm
[website]: https://www.graalvm.org/
[youtube]: https://www.youtube.com/graalvm
