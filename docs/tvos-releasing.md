# tvOS releases

Reposilite uses the self-hosted Mac on trusted `tvos-main` pushes. Pull requests use
free GitHub-hosted runners. No workflow publishes to Maven Central or the Plugin Portal.

The `reposilite` environment must allow only `tvos-main`. Set its variable
`REPOSILITE_AUTO_PUBLISH=true` to enable automatic uploads after validation. Keep it
`false` during rollout. Manual workflow dispatch defaults to a build-only rehearsal.
The runner needs labels `self-hosted, macOS, ARM64, tvos-release`, Xcode, the Android
SDK, and environment variables `TVOS_JDK21_HOME` and `TVOS_CI_GRADLE_HOME`. Publishing
loads `REPOSILITE_URL`, `REPOSILITE_USER`, and `REPOSILITE_TOKEN` from
`~/.config/tvos-reposilite.env` on the Mac. Outside contributors never run on that Mac.

Local rehearsal (builds real artifacts, validates metadata and uploads nothing):

```sh
python3 scripts/release.py reposilite --version 1.12.0-dev.20260910.1 --plugin-version 1.12.0-dev.20260910.1
```

Add `--dry-run` to print the commands without building or network access. Add
`--publish` to build, validate, reject any existing remote paths, upload, and verify
every remote file. Use a new version after any partial upload. CI assigns immutable
versions from the pinned base versions, UTC date, run ID, and attempt.

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
