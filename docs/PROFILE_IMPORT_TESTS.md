# Profile importer tests

The JVM test suite uses Robolectric to exercise the Android Base64 decoder used by the importer.

Covered cases:
- VLESS link parsing with WebSocket/TLS query fields.
- Trojan link parsing.
- Invalid port rejection.
- Batch import de-duplication and rejected-line counting.
- Base64 subscription decoding and multi-profile import.

Run with:

```bash
gradle --no-daemon :app:testDebugUnitTest
```

These tests validate parsing only. They do not establish remote reachability or VPN tunnel functionality.
