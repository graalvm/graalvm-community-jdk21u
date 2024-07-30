# GraalVM Community Backports Repositories Guidelines

1. Commits to a GraalVM Community Edition (CE) release branch or maintenance repository are either a) replicas of existing commits applied to the `master` branch in the `oracle/graal` repository or b) specific to the maintained version. Such commits are intended to fix or improve existing features, must always keep release stability in mind, and ensure compatibility with published public APIs.

2. Release branches shall be maintained by Oracle in `oracle/graal` for six months per GraalVM CE release. During that time, the community can request backports through pull requests against the corresponding release branch in `oracle/graal`.

3. After six months and only for Java LTS releases that the community wants to support long term (e.g., 21), Oracle shall create a dedicated repository (e.g., `graalvm/graalvm-community-jdk21u`) to maintain community backports and version-specific bug fixes.

4. Each repository shall be managed by one maintainer, appointed by Oracle, with the permission to approve and merge pull requests.

5. The repositories shall not be used to ship new releases of GraalVM CE or any other GraalVM distribution, only to maintain source code. Distributors should use these repositories as their primary source for creating GraalVM CE-based builds.

6. The creation of maintenance repositories for other GraalVM projects (e.g., LabsJDK or GraalJS) can also be requested by the community after six months. The same rules apply.

7. Maven artifacts under the `org.graalvm` group that are used by both GraalVM CE-based distributions and Oracle GraalVM will be maintained by Oracle.