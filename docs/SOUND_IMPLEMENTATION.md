> 公開版は咆哮音2配置だけを収録します。以下の元構成のBGM3点・レベルアップ音は非収録で、設定例はdocs/reference-resourcesにあります。

# 音声実装の読み方

この資料はWindows開発ソースにある実装と同梱設定を説明します。稼働Macの設定や完成品の動作を再現する手順ではありません。この公開版には元音声を復元しています。素材の条件は [第三者素材の通知](../THIRD_PARTY_NOTICES.md) に記載しています。

「ライブアセット」の実装済み範囲は、ゲーム中の**音声ファイル転送と再生**です。画像・モデル・フォントなど音声以外の転送・更新は実装していません。クラス名のAssetを汎用アセット管理の実装と解釈しないでください。

## 二つの経路

| 経路 | サーバー定義 | クライアントの参照先 | 音声バイトの転送 |
| --- | --- | --- | --- |
| 通常のBGM/SE | `sounds/**/*.yml` の `sound-event` | Modの `assets/eltenasound/sounds.json` → 同梱Ogg | この経路では行わない |
| ライブ音声 | `live-assets/**/*.yml` の `file` | 受信した外部Oggのローカルキャッシュ | `eltena:sound` のJSONメッセージで行う |

通常の `dragon_roar` とライブの `dragon_roar_live` はID・参照経路が別です。元Oggは同一SHA-256ですが、ライブ音声を `sounds.json` に登録する方式ではありません。

## 元の配置規則

開発ソースでの配置（公開版の収録範囲を併記）:

```text
EltenaSoundCore/src/main/resources/
  sounds/bgm/bgm_field_test.yml
  sounds/bgm/bgm_first_field.yml
  sounds/bgm/bgm_first_village.yml
  sounds/se/dragon_roar.yml
  sounds/se/level_up_test.yml
  live-assets/voice/dragon_roar_live.yml
  live-assets/files/voice/dragon_roar_live.ogg   [収録]
EltenaSound/src/main/resources/assets/eltenasound/
  sounds.json
  sounds/bgm/field_test.ogg                    [音声は非収録]
  sounds/bgm/first_field.ogg                   [音声は非収録]
  sounds/bgm/first_village.ogg                 [音声は非収録]
  sounds/se/dragon_roar.ogg                    [収録]
  sounds/se/level_up_test.ogg                  [音声は非収録]
```

元の `EltenaSoundCorePlugin` は `saveBundledResource` で未配置の同梱ファイルをプラグインデータフォルダへ保存します。既存ファイルの上書き処理ではありません。前回コピーでは初期配置2行を除去していましたが、この公開版では復元しました。説明用の元YAMLは [参照用ディレクトリ](reference-resources/EltenaSoundCore/live-assets/voice/dragon_roar_live.yml) に戻しました。docs以下はビルドのリソースに含まれず、自動配置されません。

実行時の相対配置を示す例:

```text
plugins/EltenaSoundCore/
  config.yml
  sounds/                  # 公開版の同梱定義はse/dragon_roar.ymlのみ。他4定義は資料に保持
  live-assets/
    voice/dragon_roar_live.yml
    files/voice/dragon_roar_live.ogg  # 公開版では元の咆哮を同梱
config/eltenasound/live-assets/cache/  # クライアントのCONFIGDIRを基準
  _incoming/                         # 受信途中の .part
  dragon_roar_live-v1-dragon_roar_live.ogg  # 完了後の命名例
```

サーバーの `file` はYAMLのある `voice/` からではなく、データフォルダの **`live-assets/` を基準**に解決します。パスを正規化し、基準内の通常ファイルかを確認します。YAMLは再帰探索され、`disabled`, `_disabled`, `backup`, `_backup`, `docs`, `examples`, `files` の要素を持つパスを除外します。相対パス順に読み、重複IDは最初の定義を保持します。

## 実際の設定とイベント対応

開発ソースに同梱されていた [通常音声の5定義](reference-resources/EltenaSoundCore/sounds/) と [元sounds.json](reference-resources/EltenaSound/sounds.original.json) を参照用に収録しました。個人情報・絶対パスは見つからず、値は変更していません（UTF-8 BOM・改行の正規化のみ）。これはProductionから抽出した設定ではありません。

| YAMLのid | sound-event | 元sounds.jsonのname | 元ファイル（sounds/以下） | stream |
| --- | --- | --- | --- | --- |
| bgm_field_test | eltenasound:bgm.field_test | eltenasound:bgm/field_test | bgm/field_test.ogg | true |
| bgm_first_field | eltenasound:bgm.first_field | eltenasound:bgm/first_field | bgm/first_field.ogg | true |
| bgm_first_village | eltenasound:bgm.first_village | eltenasound:bgm/first_village | bgm/first_village.ogg | true |
| dragon_roar | eltenasound:se.dragon_roar | eltenasound:se/dragon_roar | se/dragon_roar.ogg | false |
| level_up_test | eltenasound:se.level_up_test | eltenasound:se/level_up_test | se/level_up_test.ogg | false |

通常定義の実例:

```yaml
id: dragon_roar
type: se
display-name: "ドラゴンの咆哮"
sound-event: "eltenasound:se.dragon_roar"
category: SYSTEM
loop: false
volume: 1.0
pitch: 1.0
duration-ms: 3436
```

`id`, `type`, `display-name`, `sound-event`, `category`, `volume`, `pitch` を定義します。`type` は `bgm` / `se`、カテゴリは `BGM` / `SYSTEM` / `VOICE`。volumeとpitchは正値。`loop` は省略時false、`duration-ms`, `fade-in-ms`, `fade-out-ms` は省略時0で非負です。BGMの実例ではloop=true、field_testのfadeは各2000ms、first_field/first_villageは各3000msです。durationはAPIで照会できる値であり、音声データそのものではありません。

`SoundRegistry` → `SoundPlaybackService` → `SoundPluginMessenger` → `EltenaSoundNetwork` → `SoundPlaybackManager` / `ManagedSoundInstance` が通常経路です。`play_sound` のイベントIDからクライアントの音を解決します。カテゴリ停止やBGM等の処理と、下記ライブ再生の能力は同一ではありません。

## ライブYAMLの実例と読まれ方

```yaml
id: dragon_roar_live
type: voice
display-name: "ドラゴン咆哮ライブ音声"
file: "files/voice/dragon_roar_live.ogg"
version: 1
sha256: ""
category: VOICE
volume: 1.0
pitch: 1.0
duration-ms: 3436
```

| 項目 | `LiveAssetRegistry` での扱い |
| --- | --- |
| id | 空ならスキップ。定義・転送・再生要求を結ぶキー |
| type | 空または非対応ならスキップ。`LiveAssetType` の対応値はvoiceのみ |
| display-name | 必須文字列。コマンド等の表示用 |
| file | 必須。live-assets基準で実ファイルを読み取る |
| version | 必須の正整数。手で記述する値。自動採番機能ではない |
| sha256 | 空ならサーバーで実ファイルから計算。記述されていて実値と違えば警告してスキップ |
| category | 必須。BGM / SYSTEM / VOICE。typeとは別の区分 |
| volume / pitch | 必須の正値。再生ペイロードへ渡す |
| duration-ms | あれば非負の値。なければOggDurationResolverで推定し0以上にする。API照会用で、play_live_assetには送られない |

ファイル名・サイズは実ファイルから取得します。ファイル不存在などの失敗は収集され、loadが例外で終了する経路があります。前回はこの理由で参照YAMLを動作用リソースへ戻しませんでした。今回は元音声とともに復元しています。

## ゲーム中の転送と再生

1. クライアントはログイン後、プレイヤー等が存在する状態で20 tickの待機を経て `sound_client_ready`（protocolVersion=1、supportsLiveAssets=true）を送ります。サーバーはreadyを記録します。ready処理は通常のsound_configを送りますが、ここから全音声の自動取得を開始する処理は確認できません。
2. コマンドまたはAPIの `LiveAssetPlaybackService.play` はreadyを確認し、`play_live_asset` を先に送り、続いて同じ音声の全データを転送します。キャッシュがある場合もサーバー側は転送を省略しません。
3. `live_asset_transfer_start` はID、type、ファイル名、version、SHA-256、サイズ、chunkSize、totalChunks、categoryを含みます。ファイルは全体をメモリに読み、8192バイト単位に分け、0始まりindexとBase64データの `live_asset_transfer_chunk` を順次送ります。最後に `live_asset_transfer_complete` を送ります。通信チャンネルは `eltena:sound` です。
4. クライアントはメッセージをキューに積み、tickで `LiveAssetManager` へ渡します。再生要求時にキャッシュのSHA-256が一致すれば即再生、なければassetIdごとに再生要求を保留します。
5. 受信開始で `_incoming/*.part` を作成し、連続するindexのチャンクだけを追記します。完了時にチャンク数とSHA-256を照合し、一致すると最終キャッシュへ移します。順序不一致・ハッシュ不一致等では一時ファイルを破棄します。保留要求があれば再生します。
6. キャッシュ名は小文字化・文字置換した `id-v<version>-fileName`。`ExternalLiveAudioInstance` がファイルをJOrbisAudioStreamで開き、MinecraftのSoundEngine/ChannelAccessをリフレクションで取得し、STREAMINGチャンネルで再生します。相対音・減衰なし・ループなしで、volume/pitchを適用します。再生終了の監視とリソース解放があります。

「streaming」は完成受信したローカルOggをストリームとして再生する処理です。受信途中のチャンクを鳴らすネットワークストリーミングではありません。ライブ経路には位置座標、通常音声と同じfade/loop設定はありません。カテゴリ停止時のライブ音声は即停止です。

## 確認できた操作と、実装済みと扱わない機能

`EltenaSoundCommand` に `live list`, `live reload`, `live play <assetId>`（実行プレイヤー）, `live send-manifest <player>`, `live send <player> <assetId>` があります。ルートコマンドは `/eltenasound` です。`live reload` は `reloadPluginState` を呼んで定義を再読込しますが、全ファイルの自動配布ではありません。APIにはhasLiveAsset/playLiveAsset/getLiveAssetDurationMsがあります。

manifest受信はクライアント内の一覧を置き換えます。manifestとの差分を見て自動ダウンロードする処理はありません。サーバーに `request_live_asset` の受信処理はありますが、このクライアントからそれを発行する呼出しは確認できません。公開資料では「自動差分同期」と呼びません。

versionとハッシュによる識別・キャッシュ照合はありますが、ファイル監視による自動更新、差分パッチ、再送/再開、配布完了ACK、旧版の自動削除、容量制限付きキャッシュ管理、ロールバック、管理GUIの実装を示すものではありません。画像・モデル・フォントの転送・更新は未実装です。

## ソースへの入口

- [サーバー定義読込](../plugins/EltenaSoundCore/src/main/java/com/eltena/soundcore/config/LiveAssetRegistry.java)
- [転送と再生要求](../plugins/EltenaSoundCore/src/main/java/com/eltena/soundcore/playback/LiveAssetPlaybackService.java)
- [コマンド](../plugins/EltenaSoundCore/src/main/java/com/eltena/soundcore/command/EltenaSoundCommand.java) / [API](../plugins/EltenaSoundCore/src/main/java/com/eltena/soundcore/api/EltenaSoundApiService.java)
- [クライアント通信](../mods/EltenaSound/src/main/java/com/eltena/sound/network/EltenaSoundNetwork.java)
- [受信・キャッシュ・再生待ち](../mods/EltenaSound/src/main/java/com/eltena/sound/sound/LiveAssetManager.java)
- [外部Oggの再生](../mods/EltenaSound/src/main/java/com/eltena/sound/sound/ExternalLiveAudioInstance.java)

公開用sounds.jsonの標準音への置換は元の設計ではありません。この公開版のassets/eltenasound/sounds.jsonも本来の参照へ復元済みです。参照資料と上の表で対応を確認できます。標準音は元のBGM/SEの長さ・性質を再現しません。
