# 外部依存とライセンスの確認

確認日：2026-09-27。0.1.1で解決したDebug APKの依存関係を確認し、同じ依存バージョンの0.1.2で追加の権利表示を補完した。

アプリ本体のコード・文書をApache License 2.0で提供する方針と、今回確認した依存先の条件は両立する。依存先の著作権表示・ライセンス本文・付随するNOTICEなどは保持する。音源は別の権利表示であり、Apache 2.0へ変更しない。

## 確認した範囲

- Androidビルドが解決した配布APK側のMaven依存80件（直接依存・間接依存）のPOM。ListenableFutureは親POMのライセンス指定を確認。すべて主ライセンスはApache 2.0。
- 配布AAR/JARに含まれるLICENSE・NOTICEの有無と、LiteRT 1.4.2 AARのLICENSE本文。LiteRTの本文末尾にはCaffe由来のBSD形式の条件があり、その全文を同梱。
- Kotlin 2.3.20公式の権利表示。Androidで利用する共通・JVM標準ライブラリにはBSD 3-clauseおよびBoost 1.0の部分もあるため、GWT・Guavaの表示とともに本文を同梱。
- 同梱したYAMNetモデル自体のメタデータに `Apache License. Version 2.0` の記載があることと、モデルのSHA-256。
- JUnit 4.13.2はEPL 1.0で、単体テストと実機テスト用の依存。以下のアプリAPK側の依存一覧には含まれない。開発用Compose toolingはDebug APKに含まれる。

POMの主ライセンス指定だけで、ライブラリ内部の全ソースの条件が同一とは判断しない。この確認は解決済みパッケージ・配布物の表示と、上記の公式文書に基づく。ネイティブ実装を含む全ソースファイルの法的な監査や、音源生成時の契約条件の検証を行ったものではない。依存バージョンやモデルを変更するときは再確認する。

## 公開時に保持するもの

- ルートの `LICENSE`、`THIRD_PARTY_NOTICES.md`、`ASSET_LICENSES.md`。
- `third-party-licenses/` の原文と、YAMNetのライセンス。
- 依存ライブラリを改変した場合は、その変更の表示。付随NOTICEがある場合はその表示。

Apache 2.0の第4条：<https://www.apache.org/licenses/LICENSE-2.0>

BSD 2/3-clauseおよびBoost 1.0は、Apache Software FoundationのCategory A（Apache製品に含められるライセンス）にも列挙されている。これは互換性の参考であり、本アプリがASF公式プロジェクトであることを意味しない：<https://www.apache.org/legal/resolved.html#category-a>

Kotlinのバージョン別表示：<https://github.com/JetBrains/kotlin/blob/v2.3.20/license/README.md>

## 解決済みのアプリAPK側パッケージ

主ライセンスは各POMでApache 2.0と確認。内部の例外は上記および `THIRD_PARTY_NOTICES.md` を参照。

| パッケージ | バージョン |
| --- | --- |
| `androidx.activity:activity-compose` | 1.13.0 |
| `androidx.activity:activity-ktx` | 1.13.0 |
| `androidx.activity:activity` | 1.13.0 |
| `androidx.annotation:annotation-experimental` | 1.4.1 |
| `androidx.annotation:annotation-jvm` | 1.9.1 |
| `androidx.arch.core:core-common` | 2.2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 |
| `androidx.autofill:autofill` | 1.0.0 |
| `androidx.collection:collection-jvm` | 1.5.0 |
| `androidx.collection:collection-ktx` | 1.5.0 |
| `androidx.compose.animation:animation-android` | 1.10.6 |
| `androidx.compose.animation:animation-core-android` | 1.10.6 |
| `androidx.compose.foundation:foundation-android` | 1.10.6 |
| `androidx.compose.foundation:foundation-layout-android` | 1.10.6 |
| `androidx.compose.material:material-android` | 1.10.6 |
| `androidx.compose.material:material-ripple-android` | 1.10.6 |
| `androidx.compose.material3:material3-android` | 1.4.0 |
| `androidx.compose.runtime:runtime-android` | 1.10.6 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.10.6 |
| `androidx.compose.runtime:runtime-retain-android` | 1.10.6 |
| `androidx.compose.runtime:runtime-saveable-android` | 1.10.6 |
| `androidx.compose.ui:ui-android` | 1.10.6 |
| `androidx.compose.ui:ui-geometry-android` | 1.10.6 |
| `androidx.compose.ui:ui-graphics-android` | 1.10.6 |
| `androidx.compose.ui:ui-test-manifest` | 1.10.6 |
| `androidx.compose.ui:ui-text-android` | 1.10.6 |
| `androidx.compose.ui:ui-tooling-android` | 1.10.6 |
| `androidx.compose.ui:ui-tooling-data-android` | 1.10.6 |
| `androidx.compose.ui:ui-tooling-preview-android` | 1.10.6 |
| `androidx.compose.ui:ui-unit-android` | 1.10.6 |
| `androidx.compose.ui:ui-util-android` | 1.10.6 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |
| `androidx.core:core-ktx` | 1.18.0 |
| `androidx.core:core-viewtree` | 1.0.0 |
| `androidx.core:core` | 1.18.0 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 |
| `androidx.documentfile:documentfile` | 1.0.0 |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 |
| `androidx.emoji2:emoji2` | 1.4.0 |
| `androidx.graphics:graphics-path` | 1.0.1 |
| `androidx.interpolator:interpolator` | 1.0.0 |
| `androidx.legacy:legacy-support-core-utils` | 1.0.0 |
| `androidx.lifecycle:lifecycle-common-java8` | 2.10.0 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.10.0 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.10.0 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.10.0 |
| `androidx.lifecycle:lifecycle-livedata` | 2.10.0 |
| `androidx.lifecycle:lifecycle-process` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.10.0 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.10.0 |
| `androidx.loader:loader` | 1.0.0 |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.0.0 |
| `androidx.navigationevent:navigationevent-android` | 1.0.0 |
| `androidx.navigationevent:navigationevent-compose-android` | 1.0.0 |
| `androidx.print:print` | 1.0.0 |
| `androidx.profileinstaller:profileinstaller` | 1.4.0 |
| `androidx.savedstate:savedstate-android` | 1.4.0 |
| `androidx.savedstate:savedstate-compose-android` | 1.4.0 |
| `androidx.savedstate:savedstate-ktx` | 1.4.0 |
| `androidx.startup:startup-runtime` | 1.1.1 |
| `androidx.tracing:tracing` | 1.2.0 |
| `androidx.transition:transition` | 1.6.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |
| `androidx.window:window-core-android` | 1.5.0 |
| `androidx.window:window` | 1.5.0 |
| `com.google.ai.edge.litert:litert-api` | 1.4.2 |
| `com.google.ai.edge.litert:litert` | 1.4.2 |
| `com.google.guava:listenablefuture` | 1.0 |
| `org.jetbrains:annotations` | 23.0.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.3.20 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.9.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.7.3 |
| `org.jspecify:jspecify` | 1.0.0 |
