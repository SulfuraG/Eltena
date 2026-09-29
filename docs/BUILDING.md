# ビルド

必要条件: JDK 21、Gradle 9.4.1、依存リポジトリへのネットワーク接続。
第三者jarを公開物に含めないため、Gradle Wrapper jarも登録していません。
Gradle 9.4.1は [公式配布](https://gradle.org/releases/) から別途用意してください。

各モジュールは独立Gradleプロジェクトです。公開用コピーには自動配備タスクがありません。
ビルド対象や出力先は、このリポジトリの6本に限定します。

PowerShell:

```powershell
./build.ps1 -Module EltenaEffectCore
./build.ps1 -Module All -MythicMobsJar ./dependencies/MythicMobs-5.12.0.jar
```

`dependencies` はGit対象外です。MythicMobsは正規に入手した5.12.0を指定してください。
第三者の配布jarをこのリポジトリへ追加しないでください。
`-GradleExecutable` でGradle実行ファイルを明示できます。JavaはJAVA_HOMEのJDK 21を使います。

macOS/Linuxを含め、Gradleを直接使用する場合:

```sh
gradle -p plugins/EltenaEffectCore build --no-daemon
gradle -p plugins/EltenaCore build --no-daemon -PmythicMobsJar=/path/to/separately-obtained.jar
gradle -p mods/EltenaAddon build --no-daemon
```

ここに示すMacコマンドは手順例であり、Macで検証したという意味ではありません。
jarは各モジュールの `build/libs` に生成されます。起動・配備は別途明示して行う操作です。

## 再現性の範囲

Java、Gradle、ModDev、NeoForge、Parchment、直接依存の版を明示しています。
Paper APIはSNAPSHOTのため、依存解決日時で実体が変わり得ます。
私的ビルドで使用したMythicMobsのSHA-256とビルド結果は検証記録に残します。
jarのファイル順と内部日時は正規化しますが、異なる環境でのバイト単位再現ビルドは未検証です。

## 設定

`plugins/<module>/src/main/resources` のYAMLが同梱テンプレートです。
稼働環境からコピーした設定・ユーザー記録ではありません。
サンプルの音声は [素材説明](ASSETS.md) を参照してください。
