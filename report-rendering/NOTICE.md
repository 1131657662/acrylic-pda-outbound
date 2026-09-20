# 第三方资源

- Apache POI 5.4.1：Apache-2.0，Excel SXSSF生成。
- Playwright Java 1.55.0：Apache-2.0，固定Chromium 140.0.7339.16 / build1187；浏览器许可证随下载分发。
- Jackson 2.19.2、PDFBox 3.0.5：Apache-2.0。
- Noto Sans CJK SC Regular 2.004：SIL Open Font License 1.1；完整许可证位于`src/main/resources/NOTO-LICENSE.txt`。
  固定来源 `https://raw.githubusercontent.com/notofonts/noto-cjk/Sans2.004/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf`。
  原字体未经修改，以数据字体内嵌HTML并由Chromium子集嵌入PDF。构建内打包原字体和许可证；不得删去许可证后单独出售字体。

依赖具体传递版本见`gradle.lockfile`。本模块不依赖Codex桌面环境；只有本次视觉取证使用了桌面提供的Poppler和Artifact Tool。
