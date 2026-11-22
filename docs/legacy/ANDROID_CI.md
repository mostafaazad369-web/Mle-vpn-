# Android CI and APK artifact

Every push to `main`, pull request targeting `main`, or manual workflow dispatch runs a Gradle debug build and unit-test task on JDK 17.

If the build succeeds, GitHub Actions uploads the debug APK as an artifact named `MLE-VPN-debug` for seven days.

This workflow proves compilation only. It does not validate real-world VPN connectivity, censorship resistance, server availability, DNS leak behavior, or Play Store readiness. The VPN engine integration remains incomplete until the libbox service lifecycle and Android TUN callback are implemented and device-tested.
