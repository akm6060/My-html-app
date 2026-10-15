# My HTML App — HTML to APK Converter

Android project settings:
- App name: My HTML App
- Package: `com.example.myhtmlapp`
- Version: 1.0 (`versionCode` 1)
- Internet permission: enabled
- Full-screen immersive display: enabled

## What this project does
This is an Android WebView shell that lets the user import either:
- one `.html` file (saved as `index.html`), or
- a `.zip` website/project archive containing `index.html` (preferably at the archive root).

The imported project is copied into the app's private storage and opened locally. Relative CSS, JavaScript, images, fonts, and other files in a ZIP project are retained.

## Build the APK using GitHub Actions
1. Create a new GitHub repository, for example `my-html-app`.
2. Upload all files and folders in this ZIP to the repository root. Make sure `.github/workflows/build-apk.yml` is included.
3. Open the repository's **Actions** tab and enable Actions if prompted.
4. Run **Build Android APK** using **Run workflow**, or push a commit to the `main` branch.
5. Open the completed workflow run, scroll to **Artifacts**, and download `MyHtmlApp-debug-apk`.
6. Extract the downloaded artifact ZIP; install `app-debug.apk` on your Android phone. You may need to allow installation from your browser or file manager.

The first build may take several minutes. GitHub Actions builds the APK remotely; this project does not compile APKs inside Chrome.

## Build locally (optional)
Requires JDK 17, Android SDK, and Gradle 8.9:
```sh
gradle --version
gradle assembleDebug
```
APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Importing projects
- For a single HTML file, select it with **Import HTML / ZIP**.
- For a multi-file site, create a ZIP with `index.html` and its associated folders/files, then import the ZIP.
- The ZIP should contain `index.html` at its root. A single enclosing folder is also supported when it contains `index.html`.
- Imported files remain in this app's private storage. Reimport to replace the current project.

## Security notes
- ZIP extraction blocks path traversal entries.
- WebView file access is enabled to support local project assets. Import only HTML projects you trust.
- Internet permission is declared, but network access is not required for local projects.
- This is a debug APK workflow. For public distribution, create and securely store a release signing key and configure a signed release build.
