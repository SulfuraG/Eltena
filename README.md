# Eltena — 未完成のMMORPG技術資料

Pure Paperプラグイン3本とNeoForgeクライアントMod3本について、実際に作った実装・設定・ファイル構成を読むための資料です。未完成であり、完成品のサーバーパックやゲーム内動作の再現を提供するものではありません。

| サーバー / Pure Paper | クライアント / NeoForge | 主な内容 |
| --- | --- | --- |
| [EltenaCore](plugins/EltenaCore) | [EltenaAddon](mods/EltenaAddon) | 状態・成長・装備値の集約、UI・HUD・同期 |
| [EltenaEffectCore](plugins/EltenaEffectCore) | [EltenaEffect](mods/EltenaEffect) | 演出要求、シーケンス、カメラ、画面効果・シェーダー |
| [EltenaSoundCore](plugins/EltenaSoundCore) | [EltenaSound](mods/EltenaSound) | 音声要求、通常SE、ゲーム中の音声転送・再生 |

サーバーはPure Paper / Java 21、NeoForgeはクライアント側だけです。旧Youer環境やHybridの実装ではありません。判定・保存の正本はサーバー側です。

**ライブアセットとして実装されているのはゲーム中の音声転送・再生までです。画像・モデル・フォントなど音声以外の転送・更新は実装していません。** manifestの受信を自動差分同期とは扱いません。

## 収録内容

- 匿名化済みの自作6本のコード、ビルド定義、設定、技術資料。
- Modern Font Pack由来のフォント画像251点と元のフォント参照定義。Addonの専用UI用で、Minecraft全体のフォント置換ではありません。
- DRAGON-STUDIOのドラゴン咆哮音を通常SE用とライブ音声転送用の2箇所へ収録。同じ音源の2用途です。
- BGM3点とレベルアップ音は非収録。元YAMLとsounds.jsonはdocs/reference-resourcesに技術資料として残します。
- 前回検討した標準フォント・標準音への代替定義はdocs/prior-publication-substitutesに区別して保存し、実装からは参照しません。

第三者jar、ワールド、プレイヤーデータ、ログ、キャッシュ、他の自作プラグインは含みません。LifeAction系を作成済みとして扱いません。自動配備処理はなく、ビルドしても既存サーバーへコピーしません。

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
