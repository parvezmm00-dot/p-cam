# Camera Pro — Stage 1

Starter Android Camera2 project. Build is intended for GitHub Actions with Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21 and Android SDK 35.

## Build from a phone
1. Create a GitHub repository named `CameraProStage1`.
2. Upload all files/folders in this project to the repository, keeping the folder structure.
3. Open the Actions tab and enable workflows if prompted.
4. Select `Build Camera Pro APK` and tap `Run workflow` (or push a commit to main).
5. When the run succeeds, open the run, scroll to Artifacts, download `CameraPro-debug-apk`, then extract it on your phone.
6. Tap `app-debug.apk` to install. If prompted, allow your browser/file manager to install unknown apps.

## Notes and limitations
- This source has not been built or tested on a physical device in this delivery.
- Stage 1 records video at a fixed 1280x720/30fps where the device supports the requested Camera2 configuration; 4K and frame-rate selection are not implemented yet.
- Vivid is applied to saved photos only, not the live preview or video. The intensity slider adjusts saturation strength. GPU/OpenGL processing is planned for Stage 2.
- Camera2 device capabilities differ. Some phones may reject a session or the fixed video size; test on the target device.
- Do not use this prototype for important recordings until thoroughly tested.
