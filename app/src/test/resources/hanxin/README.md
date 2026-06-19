Place real Han Xin Code (汉信码) images in this directory for external decoder tests.

Supported formats: PNG, JPEG, GIF, BMP (anything Android BitmapFactory can decode).

For each image, add one line to `expected-results.txt` in this directory:

    filename.png=expected decoded text

Example:

    hanxin_hello.png=Hello
    hanxin_chinese.png=汉信码

Tests will automatically load every image listed in `expected-results.txt` and assert
that `HanXinDecoder.decode(bitmap).text` equals the expected value.

Images should ideally be:
- reasonably well lit and in focus
- have a quiet zone (white border) around the symbol
- not heavily distorted or truncated

The decoder already handles rotation, scaling, moderate blur, inversion, and small
amounts of noise, so everyday photos and screenshots are acceptable.
