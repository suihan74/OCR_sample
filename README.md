# PhoPyPe

photographically copy and paste

カメラを使ってリアルから文字列をコピペするマッシュルームアプリ。

実用性はアレなのでcamera2+Firebase MLKitサンプルということにしたもの。

<img alt="様子" src="https://raw.githubusercontent.com/wiki/suihan74/OCR_sample/images/screenshot_2.jpg" width="270"/>

検出した文字列から必要な部分を選択して、マッシュルーム機能で入力中のテキストに挿入する。

使用しているのはいくら使ってもお金がかからないオンデバイスのやつなので精度はアレだし日本語もむりぽ。


## clone後実行するまでにする必要があること

Firebaseプロジェクトの作成（ https://firebase.google.com/docs/android/setup ）を完了させた後、app/直下に「google-services.json」を配置する必要があります。
