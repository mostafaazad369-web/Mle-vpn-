# OpenVPN 3 dependency provenance

This folder contains the dependency lock used for the planned MLE OpenVPN port. It does not yet contain extracted OpenVPN or third-party source trees, generated binaries, or an active native build target.

- Exact source URLs, immutable revisions, SHA-256 values and component licenses: `source-lock.json`.
- License texts copied from MLM VPN: `app/src/main/assets/openvpn-licenses/`.
- Port plan and safety gates: `docs/OPENVPN_PORT.md`.

Do not build or ship an engine until every archive hash has been verified, all license notices are retained, the JNI bridge has been adapted and reviewed, and device-level tunnel tests pass.

Upstream: https://github.com/mlmvpn/mlmvpn_android (credit: mlmvpn).
