# Skunk SQL Support

IntelliJ IDEA plugin that restores editor type inference for Skunk's Scala 2
`sql` interpolator on Skunk 1.0.0.

## Development

```bash
./gradlew test
./gradlew buildPlugin
./gradlew verifyPlugin
```

`buildPlugin` creates an unsigned zip for local testing.

## Release

Create or reuse a plugin signing certificate using the JetBrains signing docs:

https://plugins.jetbrains.com/docs/intellij/plugin-signing.html

Then expose the signing material only through environment variables:

```bash
export CERTIFICATE_CHAIN="$(cat chain.crt)"
export PRIVATE_KEY="$(cat private.pem)"
export PRIVATE_KEY_PASSWORD="..."
```

Build the signed Marketplace artifact:

```bash
./gradlew test --no-daemon
./gradlew verifyPlugin --no-daemon
./gradlew signPlugin --no-daemon
```

Upload the signed zip from `build/distributions`.

The first Marketplace upload must be done manually. Later uploads can use:

```bash
ORG_GRADLE_PROJECT_intellijPlatformPublishingToken=... ./gradlew publishPlugin
```
