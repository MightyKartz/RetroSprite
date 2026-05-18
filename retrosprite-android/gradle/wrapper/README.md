# Gradle Wrapper

`gradle-wrapper.jar` is a binary file that this scaffolding cannot generate from text.
It must be created locally before `./gradlew` will work.

## Regenerate the wrapper jar

Option A — using a system Gradle install (≥ 8.5):

```bash
cd retrosprite-android
gradle wrapper --gradle-version 8.5 --distribution-type bin
```

Option B — copy from another local Android project:

```bash
cp /path/to/other/android-project/gradle/wrapper/gradle-wrapper.jar \
   gradle/wrapper/gradle-wrapper.jar
```

Option C — open the project in Android Studio Hedgehog (or newer); it will
materialize the wrapper jar automatically on first sync.

After the jar is in place, run `chmod +x gradlew` and `./gradlew tasks` to verify.
