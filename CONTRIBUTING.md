# Contributing to GraalVM Community Edition for JDK 21 maintenance repository

The maintenance repository for GraalVM Community Edition for JDK 21 welcomes contributions from the community that satisfy the following criteria:

1. The contributor has signed the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/). See https://www.graalvm.org/community/contributors/ for more information.
2. The contribution contains a) replicas of existing commits applied to the `master` branch in the `oracle/graal` repository or b) patches specific to the maintained version. Such commits are intended to fix or improve existing features, must always keep release stability in mind, and ensure compatibility with published public APIs.
3. The contribution is considered safe and beneficial for the GraalVM Community Edition for JDK 21. Ideally the contribution should be solving a reported issue and not be proactive.

Please note that feature requests and enhancements are not accepted in this repository. If you have a feature request or enhancement, please open an issue in [GraalVM's main repository](https://github.com/oracle/graal).

## How to Contribute

### Reporting Bugs

Open a [GitHub issue](https://github.com/graalvm/graalvm-community-jdk21u/issues/new?template=1_community_bug.yml) filling in the template with the necessary information.

### Fixing Bugs

1. Make sure an [issue report](https://github.com/graalvm/graalvm-community-jdk21u/issues/new?template=1_community_bug.yml) or [backport request](https://github.com/graalvm/graalvm-community-jdk21u/issues/new?template=0_backport_request.yml) exists in this repository explaining what the problem is and why it should be considered for fixing in the maintenance repository.
2. Make sure you have signed the [Oracle Contributor Agreement](https://oca.opensource.oracle.com/).
3. Create a pull request with the fix and reference the issue or backport request in the description.

#### Backporting Fixes

When backporting bug fixes use `git cherry-pick -x` to reference the original commit in the commit message. Additionally, please reference the upstream pull request that your changes are backporting. If the backport is partial explain which parts are being backported and why. Similarly in case of conflicts explain why they are happening and how they are resolved.