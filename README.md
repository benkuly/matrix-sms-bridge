![Docker Image CI](https://github.com/benkuly/matrix-sms-bridge/workflows/Docker%20Image%20CI/badge.svg)
# matrix-sms-bridge

This is a matrix bridge, which allows you to bridge matrix rooms to SMS with one telephone number only. It is build on top of [matrix-spring-boot-sdk](https://github.com/benkuly/matrix-spring-boot-sdk) and written in kotlin.

You need help? Ask your questions in [#matrix-sms-bridge:imbitbu.de](https://matrix.to/#/#matrix-sms-bridge:imbitbu.de)

Features:
* use with one telephone number only
* send SMS
* receive SMS
* bot for automated sms sending
    * creates rooms for you
    * writes messages for you
    * allows you to send SMS at a specific time (in future)
* provider:
    * modem (with [Gammu](https://github.com/gammu/gammu))

## User Guide
### Automated room creation
Create a room with you and `@smsBot:yourHomeServer.org` only. Now you can write `sms send --help` which gives you a help, how to use this command.

Example: `sms send -t 01749292923 "Hello World"` creates a new room with the telephone number and writes "Hello World" for you. If there already is a room with this telephone numer and you participating, then "Hello World" will send it to that room.

### Invite telephone number to matrix room
The virtual matrix users, which represents SMS numbers, have the following pattern:
```text
@sms_49123456789:yourHomeServer.org
``` 
The number `49123456789` represents the international german telephone number `+49123456789`.

You can invite these users to every room, independently of the room members. So you can also invite more than one SMS number to rooms with more than one real matrix users.

### Write to telephone numbers
After a room invite the virtual matrix users automatically join the room and every message to this room will be sent as SMS to the telephone number. The SMS contains a token (e.g. "#3"), which can be used in the answer of the SMS to route it back to the matrix room.

### What if the SMS user has no token?
The bridge can be configured to route all SMS without a valid token to a default matrix room. Note that you must invite `@smsbot:yourHomeServer` to this room.

## Admin Guide
### Configure Application Service
The Application Service gets configured with a yaml-file:
```yaml
# Database connection
org:
  neo4j:
    driver:
      uri: bolt://neo4j:7687
      authentication:
        username: neo4j
        password: secret

matrix:
  bridge:
    sms:
      # (optional) SMS messages without a valid token a routed to this room.
      # Note that you must invite @smsbot:yourHomeServer to this room.
      defaultRoomId: "!jNkGzAFIPWxxXLmhso:matrix-local"
      # (optional) Allows to map SMS messages without token, when there is only one room with this number. Default is true.
      allowMappingWithoutToken: true
      # The default region to use for telephone numbers.
      defaultRegion: DE
      # (optional) The default timezone to use for `sms send` `-a` argument
      defaultTimeZone: Europe/Berlin
      # (optional) In this section you can override the default templates.
      templates:
        # See SmsBridgeProperties.kt for the keys.
  bot:
    # The domain-part of matrix-ids. E. g. example.org when your userIds look like @unicorn:example.org
    serverName: matrix-local
  client:
    homeServer:
      # The hostname of your Homeserver.
      hostname: matrix-synapse
      # (optional) The port of your Homeserver. Default is 443.
      port: 8008
      # (optional) Use http or https. Default is true (so uses https).
      secure: false
    # The token to authenticate against the Homeserver.
    token: asToken
  appservice:
    # A unique token for Homeservers to use to authenticate requests to this application service.
    hsToken: hsToken
```

### Configure HomeServer
Add this to your synapse `homeserver.yaml`:
```yaml
app_service_config_files:
  - /path/to/sms-bridge-appservice.yaml
```

`sms-bridge-appservice.yaml` looks like:
```yaml
id: "SMS Bridge"
url: "http://url-to-sms-bridge:8080"
as_token: asToken
hs_token: hsToken
sender_localpart: "smsbot"
namespaces:
  users:
    - exclusive: true
      regex: "^@sms_.+:yourHomeServerDomain"
  aliases: []
  rooms: []
```

### Configure Provider
Currently, only modems via [Gammu](https://github.com/gammu/gammu) are supported. If you want your SMS gateway provider to be supported, look into the package [`provider`](./src/main/kotlin/net/folivo/matrix/bridge/sms/provider) to see how you can add your own provider to this bridge.

#### Gammu
First you need to add some properties to the Application Service yaml-file:
```yaml
matrix:
  bridge:
    sms:
      provider:
        gammu:
          # (optional) default is disabled
          enabled: true
          # (optional) Path to the Gammu-Inbox directory. Default is "/data/spool/inbox".
          inboxPath: "/data/spool/inbox"
          # (optional) Path to the directory, where to put processed messages. Default is "/data/spool/inbox_processed".
          inboxProcessedPath: "/data/spool/inbox_processed"
```

Your `gammu-smsdrc` should look like this:
```text
[gammu]
Device = /dev/ttyModem
LogFile = /data/log/gammu.log
debugLevel = 1

[smsd]
Service = files
LoopSleep = 3
InboxPath = /data/spool/inbox/
OutboxPath = /data/spool/outbox/
SentSMSPath = /data/spool/sent/
ErrorSMSPath = /data/spool/error/
InboxFormat = detail
OutboxFormat = detail
TransmitFormat = auto
debugLevel = 1
LogFile = /data/log/smsd.log
DeliveryReport = log
DeliveryReportDelay = 7200
HangupCalls = 1
CheckBattery = 0
```

### Using Docker container
As long as there is only one Provider, Gammu is integrated into the Docker-container: `docker pull folivonet/matrix-sms-bridge`

To see, how a docker setup of the bridge could look like, have a look at the [example](./examples/gammu).
