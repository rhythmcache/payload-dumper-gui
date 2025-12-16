# payload-dumper-gui

Windows and Android app to extract Android OTA payloads from local files or HTTP URLs.

---

[DOWNLOAD](https://github.com/rhythmcache/payload-dumper-gui/releases)

---

## Features
- Supports **payload.bin** and **OTA ZIP** files
- Extract **specific partitions directly from remote HTTP OTA URLs** without downloading the full OTA
- Extract multiple partitions simultaneously
- Cancel running extractions
- Optional limit on concurrent extractions to control CPU and I/O usage
- **SHA-256** checksum verification for extracted partitions

---

## Screenshots
### Android

![Android Screenshot 1](https://raw.githubusercontent.com/rhythmcache/payload-dumper-gui/main/images/image1.png)

![Android Screenshot 2](https://raw.githubusercontent.com/rhythmcache/payload-dumper-gui/main/images/image2.png)

![Android Screenshot 3](https://raw.githubusercontent.com/rhythmcache/payload-dumper-gui/main/images/image3.png)

![Android Screenshot 4](https://raw.githubusercontent.com/rhythmcache/payload-dumper-gui/main/images/image4.png)

### Windows

![Windows screenshot](https://raw.githubusercontent.com/rhythmcache/payload-dumper-gui/main/images/windows.png)

---

## Limitations
- Currently available only for **Windows** and **Android**
- **Multi-extent OTAs** and **differential OTAs** are not supported
- The remote server must support **HTTP Range requests** for extraction from URLs

---

## Dependencies
### External dependencies used in this project
- [payload-dumper-rust](https://github.com/rhythmcache/payload-dumper-rust.git)  
  Backend library powering the core functionality of both Windows and Android versions

- [json.h](https://github.com/sheredom/json.h.git)  
  Single-header JSON parser library

- [digest](https://github.com/rhythmcache/digest.git)  
  Single-header implementation of **SHA-256**

- [imgui](https://github.com/ocornut/imgui.git)  
  Immediate-mode graphical user interface for C++

If you prefer a command-line utility, use  
[payload-dumper-rust](https://github.com/rhythmcache/payload-dumper-rust.git), which provides the same or more features.

---

## License
- [Apache-2.0](./LICENSE.txt)
