# Merging Pull Requests

Merging a pull request requires the approval from at least one [maintainer](#current-maintainers). The maintainer should ensure that the pull request satisfies the criteria listed in the [CONTRIBUTING.md](CONTRIBUTING.md) file.

# Creating a New Release

1. Switch the `release` field in all `suite.py` files to `True` and create a pull request.
2. Once the pull request is merged, create two new tags as follows:
   ```bash
   git tag -s vm-<version> -m "Community source release of GraalVM <version> for JDK 21"
   git tag -s jdk-<jdk-version> -m "Community source release of GraalVM <version> for JDK 21"
   ```
3. Bump the version and switch the `release` field back to `False`  in all `suite.py` files and create a new pull request.

Between step 1 and 3 no new pull requests should be merged.

# Current Maintainers

* [Foivos Zakkak](https://github.com/zakkak/) (Lead maintainer)
* [Karm Babacek](https://github.com/Karm/)
* [Severin Gehwolf](https://github.com/jerboaa/)