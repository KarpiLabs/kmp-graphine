# Publishing to Maven Central

KmpGraphine is configured for automated publishing to [Maven Central](https://central.sonatype.com) using [gradle-maven-publish-plugin](https://github.com/vanniktech/gradle-maven-publish-plugin).

## Prerequisites

### 1. Create a Sonatype Central Account
- Sign up at https://central.sonatype.com
- Verify your namespace ownership (e.g., `io.karpilabs`)
- Create an API token (Settings → User Token)

### 2. Set Up GPG Keys
Maven Central requires all artifacts to be signed with a GPG key pair.

```bash
# Generate a new GPG key if you don't have one
gpg --full-generate-key

# Export your public key to Maven Central
# (Follow the Sonatype Central UI to upload)

# Export your secret key as a binary keyring for signing.secretKeyRingFile.
# Do NOT use --armor here: the Gradle signing plugin's secretKeyRingFile
# property expects the old binary GPG keyring format, not an ASCII-armored
# PGP block. Using --armor will fail with "Unable to read secret key from
# file (it may not be a PGP secret key ring)".
gpg --export-secret-keys YOUR_KEY_ID > secring.gpg
```

## Configuration

### Local gradle.properties
Create or update `gradle.properties` in your project root:

```properties
# Sonatype Central credentials
mavenCentralUsername=your_sonatype_username
mavenCentralPassword=your_api_token

# GPG signing
signing.keyId=LAST_8_CHARS_OF_KEY_ID
signing.password=your_gpg_passphrase
signing.secretKeyRingFile=/path/to/secring.gpg
```

`signing.secretKeyRingFile` must be an absolute path — Gradle does **not** expand `~`, so `~/.gnupg/secring.gpg` resolves relative to the module directory (e.g. `library/~/.gnupg/secring.gpg`) and fails with "as it does not exist". Use the fully expanded path, e.g. `/Users/you/.gnupg/secring.gpg`.

### Sonatype host: Central Portal vs. legacy OSSRH
`library/build.gradle.kts` calls `publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)`. Accounts created at https://central.sonatype.com (the current sign-up flow) are **Central Portal accounts** and have no legacy OSSRH staging profile. Using `SonatypeHost.S01` (the old default in older docs/templates) against such an account fails with:

```
Cannot get stagingProfiles for account <username>
```

`SonatypeHost.CENTRAL_PORTAL` is available in `com.vanniktech:gradle-maven-publish-plugin` 0.28.0+ (no need to bump versions). Only use `S01` if your Sonatype account predates the Central Portal migration and still has a legacy OSSRH staging profile. Background: https://github.com/vanniktech/gradle-maven-publish-plugin/issues/720

### GitHub Actions (CI/CD)
For automated publishing in CI, add secrets to your GitHub repository:

1. Go to **Settings → Secrets and variables → Actions**
2. Add the following secrets:
   - `SONATYPE_USERNAME`: Your Sonatype Central username
   - `SONATYPE_PASSWORD`: Your API token
   - `SIGNING_KEY_ID`: Last 8 characters of your GPG key ID
   - `SIGNING_PASSWORD`: Your GPG key passphrase
   - `SIGNING_SECRET_KEY_RING_FILE`: Base64-encoded content of `secring.gpg`

Create a release workflow (`.github/workflows/publish.yml`):

```yaml
name: Publish to Maven Central

on:
  release:
    types: [published]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./gradlew publishAllPublicationsToMavenCentralRepository
        env:
          ORG_GRADLE_PROJECT_mavenCentralUsername: ${{ secrets.SONATYPE_USERNAME }}
          ORG_GRADLE_PROJECT_mavenCentralPassword: ${{ secrets.SONATYPE_PASSWORD }}
          ORG_GRADLE_PROJECT_signing_keyId: ${{ secrets.SIGNING_KEY_ID }}
          ORG_GRADLE_PROJECT_signing_password: ${{ secrets.SIGNING_PASSWORD }}
          ORG_GRADLE_PROJECT_signing_secretKeyRingFile: ${{ secrets.SIGNING_SECRET_KEY_RING_FILE }}
```

## Verifying your setup before publishing

Before doing a real release, check each piece independently — publishing a version to Maven Central is permanent (you can't overwrite or delete a released version).

- **Signing config loads correctly**: `./gradlew :graphine:checkSigningConfiguration`
- **Signing actually works end-to-end**: publish to your local Maven cache (no network calls to Sonatype) and confirm it signs successfully:
  ```bash
  ./gradlew :graphine:publishDesktopPublicationToMavenLocal
  ```
  Look for `signKotlinMultiplatformPublication` / `sign<Target>Publication` tasks succeeding, and check `~/.m2/repository/io/karpilabs/` for `.asc` signature files.
- **Sonatype credentials are valid**: a read-only auth check against the Central Portal API, without publishing anything:
  ```bash
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer $(printf '%s:%s' "$mavenCentralUsername" "$mavenCentralPassword" | base64)" \
    https://central.sonatype.com/api/v1/publisher/deployments
  ```
  A `200` response with a JSON deployments list confirms the username/token pair authenticates; `401`/`403` means the credentials are wrong.

## Publishing

### Local Testing
Test publishing to your local Maven repository:

```bash
./gradlew publishAllPublicationsToMavenLocal
```

### To Maven Central
```bash
./gradlew publishAllPublicationsToMavenCentralRepository
```

Once published, the artifacts will appear on Maven Central after Sonatype's automated validation (typically within 15-30 minutes).

## Verification

View published artifacts:
- https://repo1.maven.org/maven2/io/karpilabs/kmp-graphine/

Check build details:
- https://central.sonatype.com/artifact/io.karpilabs/kmp-graphine

## Troubleshooting

**Authentication Failed:**
- Verify `mavenCentralUsername` and `mavenCentralPassword` in `gradle.properties`
- Ensure your API token is valid

**`Cannot get stagingProfiles for account ...`:**
- Your Sonatype account is a Central Portal account (no legacy OSSRH staging profile). Make sure `library/build.gradle.kts` uses `SonatypeHost.CENTRAL_PORTAL`, not `SonatypeHost.S01`. See [Sonatype host](#sonatype-host-central-portal-vs-legacy-ossrh) above.

**Signature Failed:**
- Verify GPG key is properly installed: `gpg --list-secret-keys`
- Ensure passphrase is correct
- Check `signing.secretKeyRingFile` path is absolute

**Already Published:**
- Maven Central doesn't allow republishing the same version
- Increment version in `library/build.gradle.kts` and publish again

## References

- [gradle-maven-publish-plugin Docs](https://vanniktech.github.io/gradle-maven-publish-plugin/)
- [Sonatype Central Guide](https://central.sonatype.org/publish/requirements/)
- [GPG Best Practices](https://docs.github.com/en/authentication/managing-commit-signature-verification/about-commit-signature-verification)
