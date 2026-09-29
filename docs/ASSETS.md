# 収録素材と元の参照関係

画像はModern Font Pack由来の251点と元のEltenaAddonフォント定義を収録しています。標準フォントへの置換版は実行対象ではなく、`prior-publication-substitutes` に過去の代替例として区別しています。

音声はドラゴンの咆哮だけを、EltenaSoundの通常SEとEltenaSoundCoreのゲーム中ライブ転送用の2配置で収録しています。BGM3点とレベルアップ音は非収録です。元のYAMLとサウンドイベント定義は `reference-resources` に残しています。

- [素材別ライセンスと出典](../THIRD_PARTY_NOTICES.md)
- [収録253素材のパス・容量・SHA-256](included-assets.json)
- [音声の配置・設定・転送・再生の実装](SOUND_IMPLEMENTATION.md)
- [公開用変更と元実装との差分](PUBLICATION.md)

ライブ転送の実装範囲はゲーム中の音声転送・再生です。画像・モデル・フォントの転送や更新は実装していません。資料中の非収録音声の参照は、その音声の公開や利用許諾を意味しません。
