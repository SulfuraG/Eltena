# Third-party asset notices

Root LICENSE (MIT, Copyright 2026 SulfuraG) covers original Eltena code. It does not relicense third-party fonts, audio, or their derived resource definitions.

## Modern Font Pack / Noto

- Distributor: YutaYamamoto212; pack: MFP-JH-1424J.zip.
- Source: https://www.curseforge.com/minecraft/texture-packs/modern-font-pack/files/7288419
- Project: https://github.com/YutaYamamoto212/ModernFontPack
- Included: 251 PNG files under mods/EltenaAddon/src/main/resources/assets/eltenaaddon/textures/font/ and the derived ui.json font mapping, including its documentation copy.
- Original font families identified by the pack: Google Noto Sans, Noto Emoji, Noto Sans Math and Noto Sans Symbols.
- Preserve the font's [original OFL notice](THIRD_PARTY/ModernFontPack/OFL.txt), the pack's [MIT notice](THIRD_PARTY/ModernFontPack/MIT-pack.txt), and [provenance](THIRD_PARTY/ModernFontPack/NOTICE.md). The OFL-covered font is not relicensed as Eltena MIT code.
- PNG bytes are unchanged from the source pack; the font mapping scopes references to the EltenaAddon namespace.

## Epic Dragon Roar

- Creator: DRAGON-STUDIO.
- Title: Epic Dragon Roar (Pixabay asset 364481).
- Source: https://pixabay.com/sound-effects/film-special-effects-epic-dragon-roar-364481/
- License: Pixabay Content License, https://pixabay.com/service/terms/ (summary: https://pixabay.com/service/license-summary/).
- Included at mods/EltenaSound/src/main/resources/assets/eltenasound/sounds/se/dragon_roar.ogg and plugins/EltenaSoundCore/src/main/resources/live-assets/files/voice/dragon_roar_live.ogg.
- The locally converted Ogg is used for the original normal-SE and in-game live-audio-transfer examples. Both files are identical. Original metadata is retained.
- This audio is not an original Eltena composition, is not MIT-licensed, and is not offered as an unrestricted sound library. The source license's conditions, including its standalone redistribution restriction, remain applicable. Repository inclusion does not grant additional reuse rights.

## Audio not included

The original BGM files field_test.ogg, first_field.ogg, first_village.ogg and level_up_test.ogg are absent. Their original configuration examples and event references remain in docs/reference-resources for technical reading; they do not include or license the corresponding audio.
