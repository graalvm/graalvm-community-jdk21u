# Building GraalVM Community Edition for JDK 21

To build GraalVM Community Edition for JDK 21, you need to have an OpenJDK 21 build, along with static libraries, installed on your system. You can use the [AdoptOpenJDK](https://adoptopenjdk.net/) distribution or any other OpenJDK 21 build as long as it ships static libraries.

After you have the required OpenJDK 21 build, follow these steps to build GraalVM Community Edition for JDK 21:

```bash
export JAVA_HOME=/path/to/your/jdk21
git clone https://github.com/graalvm/graalvm-community-jdk21u graalvm-jdk21
export MX_VERSION=$(jq -r .mx_version graalvm-jdk21/common.json)
git clone https://github.com/graalvm/mx.git mx --branch $(MX_VERSION)
cd graalvm-jdk21
../mx/mx --primary-suite vm --env ce build
```

Once the build completes successfully, you can find the GraalVM Community Edition distribution in the directory where the `sdk/latest_graalvm_home` link points to.

