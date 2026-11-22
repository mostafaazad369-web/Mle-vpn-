# HTTPS subscription import

MLE accepts a subscription URL only when it uses HTTPS.

Safety limits:
- Maximum response body: 1,000,000 bytes.
- Connection timeout: 10 seconds.
- Read timeout: 15 seconds.
- At most three redirects.
- Redirects must remain HTTPS and cannot include URL user-info credentials.
- HTTP error responses, empty responses, and oversized responses are rejected.
- Subscription URLs and response bodies must never be written to logs.

After fetching, the existing batch parser validates individual share links, de-duplicates accepted source URIs, and stores accepted profiles in the encrypted profile vault.

This is subscription downloading and parsing only. It does not test server reachability, establish a VPN tunnel, or confirm that any imported profile can connect.
