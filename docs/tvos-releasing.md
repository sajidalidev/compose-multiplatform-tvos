# tvOS releases

Both Reposilite and Central are manual-only. No self-hosted runner or publishing
workflow is used. PR checks run on free GitHub-hosted runners. Both release
entry points reject CI. Run with a JDK 21 `JAVA_HOME`, Xcode, and the Android SDK.

Local rehearsal (builds real artifacts, validates metadata and uploads nothing):

```sh
python3 scripts/release.py reposilite --version 1.12.0-dev.20260910.1 --plugin-version 1.12.0-dev.20260910.1
```

Add `--dry-run` to print the commands without building or network access. Add
`--publish` to build, validate, reject any existing remote paths, upload, and verify
every remote file. Use a new version after any partial upload. Choose an unused version explicitly for each upload. Load `REPOSILITE_URL`,
`REPOSILITE_USER`, and `REPOSILITE_TOKEN` from your local credentials file before
using `--publish` (for example, `set -a; source ~/.config/tvos-reposilite.env; set +a`).

Central is manual-only and requires explicit versions:

```sh
python3 scripts/release.py central --version 1.12.0 --plugin-version 1.12.0 --dry-run
export PUBLISH_SIGNING_KEY_ID=YOUR_GPG_KEY_ID
# Set PUBLISH_SIGNING_PASSWORD privately if the key has a passphrase.
python3 scripts/release.py central --version 1.12.0 --plugin-version 1.12.0
```

This requires a clean tracked tree, builds into fresh staging, checks the publication
groups, versions and metadata payloads, requires sources/javadoc archives for binary
publications, and signs a Central bundle. Review the staging output before uploading.
To build and upload in one manual invocation, add `--publish` and set `CENTRAL_TOKEN`
to the Base64-encoded Portal token username/password pair. The uploader waits for
`PUBLISHED` and saves the deployment ID so status polling can be resumed. Both scripts
reject CI. No real Central upload is part of CI verification.
