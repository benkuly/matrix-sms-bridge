# matrix-sms-bridge

This is a matrix bridge, which allows you to bridge matrix rooms to SMS with one telephone number only.

It is build on top of [matrix-spring-boot-sdk](https://github.com/benkuly/matrix-spring-boot-sdk) and written in kotlin.

Currently, only modems are supported. If you want your SMS gateway provider to be supported, look into the package [`provider`](./src/main/kotlin/net/folivo/matrix/bridge/sms/provider) and [`SmsBridgeConfiguration`](./src/main/kotlin/net/folivo/matrix/bridge/sms/SmsBridgeConfiguration.kt) to see how you can add your own provider to this bridge.