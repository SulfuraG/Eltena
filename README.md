# Eltena — 未完成のMMORPG技術資料

Pure Paperプラグイン3本とNeoForgeクライアントMod3本について、実際に作った実装・設定・ファイル構成を読むための資料です。未完成であり、完成品のサーバーパックやゲーム内動作の再現を提供するものではありません。

| サーバー / Pure Paper | クライアント / NeoForge | 主な内容 |
| --- | --- | --- |
| [EltenaCore](plugins/EltenaCore) | [EltenaAddon](mods/EltenaAddon) | 状態・成長・装備値の集約、UI・HUD・同期 |
| [EltenaEffectCore](plugins/EltenaEffectCore) | [EltenaEffect](mods/EltenaEffect) | 演出要求、シーケンス、カメラ、画面効果・シェーダー |
| [EltenaSoundCore](plugins/EltenaSoundCore) | [EltenaSound](mods/EltenaSound) | 音声要求、通常SE、ゲーム中の音声転送・再生 |

サーバー側はPure Paper / Java 21、クライアント側はNeoForgeを使います。プレイヤーの状態や判定はサーバーで管理し、画面表示や音声などをクライアントで処理します。

ゲーム中にサーバーから音声ファイルを送り、クライアントで再生する仕組みも収録しています。画像・モデル・フォントの転送機能はありません。

## 収録内容

- 上記6本のソースコード、設定例、ビルド手順と実装の解説。
- EltenaAddonの画面表示で使うフォント画像と、その文字の割り当て設定。フォント画像はModern Font Pack由来です。
- ドラゴンの咆哮音。通常の効果音として鳴らす処理と、ゲーム中にサーバーから音声を転送して鳴らす処理の両方で使用します。

BGMとレベルアップ音のファイルは収録していません。音声設定の書き方と参照関係は[音声実装資料](docs/SOUND_IMPLEMENTATION.md)で確認できます。素材の出典と利用条件は[第三者素材の通知](THIRD_PARTY_NOTICES.md)を参照してください。

## 読み方

- [構成・依存関係](docs/ARCHITECTURE.md)
- [音声配置・YAML・転送と再生](docs/SOUND_IMPLEMENTATION.md)
- [公開内容と元実装からの変更](docs/PUBLICATION.md)
- [ビルド手順](docs/BUILDING.md) / [制限事項](docs/LIMITATIONS.md)
- [素材の出典・ライセンス](THIRD_PARTY_NOTICES.md)
- [公開ファイル一覧](docs/FILE_LIST.md) / [検証結果](docs/VALIDATION.md)

## ライセンス

自作コードは [MIT](LICENSE)、Copyright (c) 2026 SulfuraGです。
**同梱画像・音声および第三者由来の定義には、コード用MITを一括適用しません。** [第三者素材の通知](THIRD_PARTY_NOTICES.md)に示す各素材の条件が適用されます。

## 開発支援

実装や資料が役立った場合は [GitHub Sponsors](https://github.com/sponsors/SulfuraG) から任意に支援できます。支援は機能完成・優先対応・更新頻度・納期を保証しません。
