# PDF Viewer For Android

A lightweight and efficient PDF reader designed for Android devices with a beautiful, modern Material Design UI.

## Features

### Core Features
- **Lightweight & Fast** - Minimal storage footprint with quick PDF rendering
- **Beautiful UI** - Modern Material Design 3 interface with smooth animations
- **Easy Navigation** - Intuitive swipe gestures for page navigation
- **Zoom Controls** - Pinch-to-zoom and floating zoom buttons for precise control
- **Page Indicator** - Always know your position in the document
- **Recent Files** - Quick access to recently opened PDFs
- **Secure & Reliable** - Your documents stay private and accessible

### Technical Features
- Material Design 3 (Material You) theming
- Responsive layout optimized for all screen sizes
- Smooth scrolling and page transitions
- Efficient memory management for large PDFs
- Support for all PDF versions
- Double-tap to zoom
- Horizontal and vertical scrolling modes
- Page thumbnails via scroll handle

## Screenshots

### Main Screen
The home screen features a clean, modern design with:
- Welcome header with app branding
- Feature highlights in a beautiful grid layout
- Recent files section for quick access
- Prominent "Open PDF" button

### PDF Viewer
The PDF viewer provides:
- Full-screen reading experience
- Floating zoom controls
- Page indicator in toolbar
- Smooth page transitions
- Scroll handle for quick navigation

## Architecture

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Material Components for Android
- **PDF Rendering**: android-pdf-viewer library by barteksc
- **Architecture**: MVVM pattern ready
- **Minimum SDK**: Android 7.0 (API 24)
- **Target SDK**: Android 14 (API 34)

### Project Structure
```
app/
├── src/
│   └── main/
│       ├── java/com/pdfviewer/android/
│       │   ├── MainActivity.kt          # Main landing screen
│       │   └── PdfViewerActivity.kt     # PDF viewer screen
│       ├── res/
│       │   ├── layout/
│       │   │   ├── activity_main.xml    # Main screen layout
│       │   │   └── activity_pdf_viewer.xml # PDF viewer layout
│       │   ├── values/
│       │   │   ├── colors.xml           # Color palette
│       │   │   ├── strings.xml          # String resources
│       │   │   ├── themes.xml           # App themes
│       │   │   └── dimens.xml           # Dimensions
│       │   └── mipmap/                  # App icons
│       └── AndroidManifest.xml
└── build.gradle
```

## Building the App

### Prerequisites
- Android Studio Arctic Fox or newer
- JDK 8 or higher
- Android SDK with API level 34
- Gradle 8.1.0 or newer

### Build Instructions

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd guide
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory
   - Click OK

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - If not, click "File" → "Sync Project with Gradle Files"

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run on device/emulator**
   - Connect an Android device or start an emulator
   - Click the "Run" button in Android Studio
   - Or use command line:
   ```bash
   ./gradlew installDebug
   ```

### Build Variants
- **Debug**: Development build with debugging enabled
- **Release**: Production build with ProGuard optimization

## Permissions

The app requires the following permissions:

- `READ_EXTERNAL_STORAGE` (Android 10 and below) - To access PDF files
- `READ_MEDIA_IMAGES` (Android 13+) - For media access
- `MANAGE_EXTERNAL_STORAGE` (Optional) - For full file system access

The app uses scoped storage on Android 11+ for better security and privacy.

## Usage

### Opening a PDF
1. Launch the app
2. Tap the "Open PDF" button
3. Select a PDF file from your device
4. The PDF will open in the viewer

### Navigation
- **Swipe** vertically to scroll through pages
- **Pinch** to zoom in/out
- **Double-tap** to quick zoom
- **Tap** the page indicator to see your current position

### Zoom Controls
- **+ Button** - Zoom in (up to 500%)
- **- Button** - Zoom out (down to 50%)
- **Pinch gesture** - Custom zoom level
- **Double-tap** - Toggle between fit-to-width and zoomed view

## Customization

### Themes
The app uses Material Design 3 theming. You can customize colors in `app/src/main/res/values/colors.xml`:

```xml
<color name="primary">#6366F1</color>
<color name="secondary">#EC4899</color>
<color name="accent">#8B5CF6</color>
```

### Features to Add
Potential enhancements for future versions:
- Dark mode toggle
- Text search within PDFs
- Bookmarks and annotations
- PDF page thumbnails grid
- Share and print functionality
- Multiple file management
- Password-protected PDF support
- Night mode for reading
- Text selection and copy
- Page rotation

## Dependencies

```gradle
// Core Android
androidx.core:core-ktx:1.12.0
androidx.appcompat:appcompat:1.6.1

// Material Design
com.google.android.material:material:1.11.0

// Layout
androidx.constraintlayout:constraintlayout:2.1.4
androidx.recyclerview:recyclerview:1.3.2

// PDF Rendering
com.github.barteksc:android-pdf-viewer:3.2.0-beta.1
```

## Performance

### Optimization Features
- Lazy loading of PDF pages
- Efficient memory management
- Hardware acceleration enabled
- ProGuard minification in release builds
- Optimized for low-end devices

### File Size
- APK size: ~2-3 MB (release build)
- Minimal runtime memory footprint
- Fast cold start time

## Troubleshooting

### Common Issues

**PDF doesn't open**
- Ensure the file is a valid PDF
- Check storage permissions are granted
- Try restarting the app

**Slow performance**
- Large PDFs (100+ pages) may take longer to load
- Close other apps to free up memory
- Reduce zoom level for smoother scrolling

**Permission errors**
- Go to Settings → Apps → PDF Viewer → Permissions
- Enable storage permissions
- For Android 11+, you may need to grant "All files access"

## License

This project is open source and available under the MIT License.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

### Development Guidelines
- Follow Kotlin coding conventions
- Use Material Design guidelines
- Test on multiple Android versions
- Ensure backward compatibility
- Document new features

## Support

For bug reports and feature requests, please open an issue on the GitHub repository.

## Acknowledgments

- [android-pdf-viewer](https://github.com/barteksc/AndroidPdfViewer) by Bartosz Schiller
- Material Design by Google
- Android Open Source Project

## Changelog

### Version 1.0 (Current)
- Initial release
- PDF viewing with zoom controls
- Material Design 3 UI
- File picker integration
- Page navigation
- Efficient rendering

---

**Perfect for work, study, or leisure reading - PDF Viewer For Android keeps your documents organized, secure, and always accessible!**
