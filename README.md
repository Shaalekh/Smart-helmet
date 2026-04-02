# Smart-helmet
App code for the smart helmet application

To do yourself. Here are your options ranked by effort:

Option 1: Build it yourself (30 minutes, free)
You need Flutter installed on a Windows/Mac/Linux machine.

# 1. Install Flutter: https://docs.flutter.dev/get-started/install
# 2. Unzip the project, navigate into it
cd helmet_monitor

# 3. Get dependencies
flutter pub get

# 4. Connect Android phone via USB (enable USB debugging)
# 5. Build release APK
flutter build apk --release

# Output will be at:
# build/app/outputs/flutter-apk/app-release.apk


That APK can be side-loaded onto any Android phone.

Option 2: Use GitHub + Codemagic CI (free tier, no local setup)
	1.	Push the project to a GitHub repo
	2.	Sign up at codemagic.io — free tier includes 500 build minutes/month
	3.	Connect the repo, select Flutter app, trigger a build
	4.	Download the APK from the build artifacts
No software installation needed on your machine.

Option 3: Use a cloud IDE
Replit or GitPod can run Flutter builds in the browser. Less reliable for mobile but works.

Recommended for you: Option 1 if you have a laptop available, Option 2 if you want zero local setup. The Codemagic route is genuinely easy — it reads the pubspec.yaml automatically and produces a downloadable APK within about 10 minutes of setup.
