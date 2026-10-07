# GitHub Actions Release Workflow

This repository includes a manual release workflow configured at `.github/workflows/release.yml`.

## How to Trigger a Release Manually

1. Open your repository on GitHub.
2. Click on the **Actions** tab in the top navigation bar.
3. In the left sidebar, click **Release New Version**.
4. Click the **Run workflow** dropdown button on the right.
5. Fill in the release details:
   - **Release version tag**: (e.g. `v1.0.0` or `v1.1.0`)
   - **Release title**: (e.g. `Samsung Phone v1.0.0`)
   - **Changelog / Release notes**: List new features, fixes, or improvements
   - **Artifacts to build**: Choose `both` (APK + AAB), `apk`, or `aab`
   - **Publish as draft**: Check if you want to review before publishing
   - **Mark as pre-release**: Check for beta or alpha releases
6. Click **Run workflow**.

## What the Workflow Does

1. Checks out the repository and sets up Java 17 and Rust.
2. Validates and runs tests on both the Rust telephony core and Android Kotlin unit tests.
3. Automatically prepares a release signing keystore (uses your secrets if provided, or generates a self-signed release key so builds always succeed).
4. Compiles the signed release APK (`SamsungPhone-vX.X.X.apk`) and Android App Bundle (`SamsungPhone-vX.X.X.aab`).
5. Generates SHA-256 checksums (`SHA256SUMS.txt`).
6. Creates a new GitHub Release with the tag and attaches all compiled binaries for instant download!

## Optional: Custom Play Store Signing Key

If you want to sign releases using your own production key:
1. Go to repository **Settings** → **Secrets and variables** → **Actions**.
2. Add the following repository secrets:
   - `KEYSTORE_BASE64`: Your `.jks` file encoded in base64 (`base64 -w 0 my-key.jks`)
   - `STORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key alias password
