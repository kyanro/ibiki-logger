# いびきログ

Pixel 10 Pro XL / Android 17 で使う、端末内で完結する夜間録音アプリ。

## できること

- 寝る前に開始し、画面を消しても録音を継続する。
- 端末内の YAMNet でいびき候補を判定し、前後約2秒を含めて保存する。静かな区間の音声は保存しない。
- 夜全体の時刻付きタイムライン、区間ごとの波形、再生を用意する。
- AAC / M4A 音声と、解析用の JSON / CSV を ZIP にまとめて書き出す。
- 録音の中断を無音と混同せず記録する。
- 感度を3段階から選べる。「音だけで検出」は、会話や環境音を含めて拾う調整用モード。
- 記録ごとの削除。通信・ログイン不要。音声をクラウドへ自動送信しない。
- Wabigashi Fumi「にゃーにゃーにゃー」の任意再生BGM。音源はAPKに同梱し、オフラインで再生できる。

AndroidのアプリIDと名前空間は **`com.kyanro.ibiki_logger`**。既存アプリ `com.kyanro.sim_speed_viewer` と共通の命名規則。WebサイトやDNSの設定は不要。Play配信や署名鍵の管理は別途必要になる。

## 最初の一晩

1. 枕元の、マイクがふさがれない位置に端末を置く。初期版は充電しながら使う。
2. 感度は「標準」、「音だけで検出」はオフで開始する。
3. 「録音を開始」を押し、マイクと録音中の通知を許可する。画面を消してよい。
4. 朝にアプリまたは通知から停止する。履歴を開き、区間の波形と音を確認する。
5. 「音声＋解析データを書き出す」で「ダウンロード」などにZIPを保存する。USBなどでPCへコピーできる。

判定は候補であり、取りこぼし・誤検出がある。同室の別の人のいびきとの識別は行わない。「候補の目安」はモデルが反応した判定窓の長さの集計であり、実際のいびき時間の正確な測定値ではない。日ごとの比較は同じ置き場所で行う。

1回の録音は最大12時間。中断・マイクの消音・容量不足は記録する。強制終了時は最大30秒程度の直近の情報や保存前の区間が失われる可能性がある。

## 書き出し形式

| ファイル | 内容 |
| --- | --- |
| `audio/*.m4a` | AAC-LC、16 kHz、モノラル、目標32 kbps。保存音声1時間あたり約14.4 MB＋付随データ |
| `session.json` | 録音開始UTC時刻・端末・設定・状態・中断・全区間・波形 |
| `clips.csv` | 音声ファイル名、夜全体に対する開始位置、UTC時刻、長さ、判定スコア |
| `waveform.csv` | 100 msごとのピーク値とRMS。音声圧縮前の値 |
| `README.txt` | フィールドの意味と解釈上の注意 |

無音を詰めたファイルの時刻ではなく、元の録音開始時点からの位置を保存する。RMSはdBFS（デジタル値）で、校正された騒音計のdB SPLではない。画面の波形は全区間共通の対数尺度。

内部保存では、波形を区間ごとの不変ファイルに分け、録音中に全波形を書き直さない。モデル判定は音のあるときに約0.5秒間隔、画面更新も抑えている。ただしマイクの常時監視と録音中のCPU動作は必要で、実際の電池消費は一晩の計測が未実施。

## 開発

Kotlin / Jetpack Compose、compileSdk・targetSdk 37、minSdk 29、AGP 9.2.1、Gradle 9.4.1。依存バージョンは固定。モデルの配布元・ハッシュ・ライセンスは [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

前提はAndroid SDK、JDK 17以降、[公式Android CLI](https://developer.android.com/tools/agents/android-cli/download)。この環境ではAndroid Studio付属JDKを利用し、Gradleがビルド用JDK 17を解決する。

```powershell
# ビルド・単体テスト・静的チェック
.\scripts\build.ps1

# USBデバッグを許可した端末1台にインストールして起動
.\scripts\install.ps1
```

Android CLIはプロジェクト内の `.tools/android.exe` またはPATHから探す。ビルドキャッシュは `.tools/gradle-home`。各PCで `JAVA_HOME` / `ANDROID_HOME` を指定できる。アプリの雛形は `android create` で生成。ソースのコンパイルはGradle、APKのインストール・起動は `android run --apks=...`、表示確認は `android screen capture` / `android layout` を使った。

実機の自動テストを再実行する場合は、録音中ではない端末を使用する。`connectedDebugAndroidTest` はテスト後にアプリをアンインストールする場合があるため、大切な記録がある端末では先にZIPを書き出す。

確認結果と残りの実測項目は [docs/verification.md](docs/verification.md)。

録音した音声・個人の計測データ・開発ツール本体・ビルド成果物は Git に含めない。同梱BGMは権利表示とともに管理する。

## ふみちゃんの歌

ホーム画面下部の「BGMを再生」から、Wabigashi Fumi / OTOGI SHIFT [「にゃーにゃーにゃー」](https://otogishift.com/songs/nyaa-nyaa-nyaa/) を聴けます。同じ音源をsim-speed-viewerにも収録しています。

初期状態はオフで、アプリ画面を開いている間だけ小さめの音量で繰り返し再生します。録音開始、画面消灯、別のアプリへの移動、通話などによる音声の割り込みで停止します。録音停止後やアプリに戻ったときには自動再開しません。録音中はBGMを再生できません。音量は端末のメディア音量でも調整できます。

曲紹介ページは、ボタンを押すと外部ブラウザで開きます。アプリ内の録音・BGM再生に通信は不要です。

## 作者からのにゃーお願い（ライセンス条件ではありません）

これは、作者からの遊び心を込めたお願いです。法的な義務や強制力はなく、守らなくても Apache License 2.0 に基づくコードと文書の利用・改変・再配布の権利には一切影響しません。

- このアプリをビルドした方は、ぜひ一度、Wabigashi Fumi [「にゃーにゃーにゃー」](https://otogishift.com/songs/nyaa-nyaa-nyaa/) を聴いてみてください。
- 曲を気に入った方は、録音前や朝の振り返りのひとときに、アプリのBGMボタンから楽しんでみてください。

守っていただけたら、作者がとても喜びます。にゃー。

## ライセンス

Copyright 2026 kyanro

ソースコードと文書は、sim-speed-viewerと同じ [Apache License 2.0](LICENSE) の下で提供します。収録音源はApache License 2.0の対象外です。音源の権利表示は [ASSET_LICENSES.md](ASSET_LICENSES.md)、YAMNetなどの同梱物は [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) を参照してください。

これらのライセンス文書はAPKにも同梱し、アプリの「ライセンスとクレジット」から確認できます。

主な外部依存はAndroidX／Jetpack Compose（画面・Android連携）、LiteRT（推論実行）、YAMNet（音の分類モデル）、Kotlin標準ライブラリとCoroutinesです。各ライブラリの主ライセンスはApache 2.0ですが、LiteRTとKotlinにはBSD形式やBoost 1.0の部分もあるため、それらの権利表示・本文も同梱しています。依存先の権利を保持したうえで、このアプリのコードと文書をApache 2.0で提供します。

確認した依存関係と範囲は [docs/dependency-licenses.md](docs/dependency-licenses.md) に記載しています。
