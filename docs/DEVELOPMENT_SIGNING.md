# NightMaster development signing

Debug APK builds use a repository-scoped GitHub Actions cache entry named
`nightmaster-development-signing-v1`.

On a cache miss the workflow creates a dedicated Android development keystore
at `~/.android/debug.keystore`. The cache post-step stores that file for later
builds. Subsequent debug APKs therefore use the same signing identity and can
be installed as updates over the first cached-key build.

The keystore itself is not committed to the public repository and is not
included in APK artifacts. `BUILD_INFO.txt` records only its SHA-256 fingerprint
for provenance checks.

This mechanism is for development and device testing only. A release build
must use a separately generated private release key stored in GitHub Actions
secrets, with backup and recovery procedures owned by the application owner.
