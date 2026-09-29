# 公開内容と変更点

対象はEltenaCore / EltenaEffectCore / EltenaSoundCore / EltenaAddon / EltenaEffect / EltenaSoundの6本だけです。

画像251点と元ui.jsonを収録し、ドラゴン咆哮を通常SE・ライブ転送の2用途に残しました。BGM3点とレベルアップ音のバイナリは非収録です。元設定はdocs/reference-resourcesに保持し、通常のsounds.jsonと初期配置対象は咆哮だけに絞っています。SoundCoreで非収録音声のYAMLを自動配置する4行も除去しました。ライブ音声YAML/Oggの初期配置は元実装のままです。

元実装からのその他の変更:

- Core/AddonのJava namespace、author表記、固定PCパスの匿名化。Mod ID・通信チャンネル・保存キーは維持。
- 自動配備タスクの除去。MythicMobsは別途取得したjarをビルド引数で指定。
- 未使用の旧Items連携2クラスの除外、Coreの起動時自己検証の既定値false化。
- Effectの壊れた日本語JSONを修復。キー・書式引数は維持。
- アーカイブの順序・時刻の設定、改行・末尾空行の正規化。
- 自作コードのMIT、Copyright 2026 SulfuraG。素材は別ライセンス。

前回の標準フォント・標準音の代替は実行時リソースには使いません。比較資料だけをdocs/prior-publication-substitutesに残しています。

Windows開発ソースを基準としています。既存build jarとTest配置jarの一致はソースからの再現ビルドやMacの最新配置との対応を証明しません。Macは調査していません。ゲーム内動作検証・サーバー起動・Production変更は行っていません。

docs/source-provenance.jsonは元ソースの来歴、publication-manifest.jsonは公開対象のファイル単位ハッシュです。第三者素材の詳細はルートTHIRD_PARTY_NOTICES.mdを参照してください。
