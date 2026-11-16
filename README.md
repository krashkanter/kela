# Kela

Requirements:

- JDK 25, Android SDK 37
- Android 12+
- Root ADB, Debian chroot at `/data/local/debian`, Docker, Python 3
- `/data/local/server/debian` chroot wrapper

```powershell
.\gradlew.bat :app:assembleDebug
.\tools\install.ps1 -Serial <serial> -SkipBuild
```

```sh
python3 -m unittest discover -s server
```