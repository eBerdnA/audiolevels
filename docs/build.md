# Release

```bash
keytool -genkey -v -keystore app/my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias andre_bering_audiolevels
openssl base64 -in app/my-release-key.jks | tr -d '\n' | pbcopy
./gradlew assembleRelease
```