**Gaku** (学 - がく: learning; scholarship; study; erudition; knowledge; education) is a fork of [Kaku](https://github.com/0xbad1d3a5/Kaku), a system-wide Android OCR and Dictionary application. I do not intend to maintain this project, so feel free to contribute however you see fit. Note that the codebase was largely vibe-coded to produce a minimum-viable-product, as I am not an Android developer.


### Why this fork?
The original Gaku application relies on older OCR technology. Gaku replaces the OCR engine with **Google MLKit**, providing:
- Significantly faster recognition.
- Higher accuracy on Japanese text.
- Modern Android support (Android 13/14 compatibility).

This, unfortunately, does means there is no "kanji alternative" selection screen, so if a character is misread, it will not be possible to correct it (although this is a lesser issue with the Google OCR, as it is much more accurate). I might consider adding back an  updated version of the older Tesseract OCR backend to provide this functionality. Some future plans might include integration with AnkiConnect4Android, Yomichan dictionary support and audio recording, making this application ideal for Visual-novel imersion on-the-go with apps such as Winlator.

### Credits & License
This project is based on **Kaku**, originally developed by [0xbad1d3a5](https://github.com/0xbad1d3a5).

Licensed under the **BSD 3-Clause License**.
See the [LICENSE](LICENSE) file for the full original license text.
