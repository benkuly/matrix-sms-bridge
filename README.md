# matrix-sms-bridge

This is a matrix bridge, which allows you to bridge matrix rooms to SMS with one telephone number only. It is build on top of [matrix-spring-boot-sdk](https://github.com/benkuly/matrix-spring-boot-sdk) and written in kotlin.

## User Guide
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
The bridge can be configured to route all SMS without a valid token to a default matrix room.

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
      templates:
        # (optional) The message, that will be sent as SMS. Valid placeholders are {sender}, {body} and {token}.
        outgoingMessage: "{sender} wrote:\n\n{body}\n\nTo answer to this message add this token to your message: {token}"
        # (optional) The message, that will be sent as SMS, when an incoming SMS didn't contain a valid token
        # and was routed to a default room. By default no answer will be sent.
        answerInvalidTokenWithDefaultRoom: "Your token was invalid. The message will be sent to a default matrix room."
        # (optional) The message, that will be sent as SMS, when an incoming SMS didn't contain a valid token
        # and no default room is configured.
        answerInvalidTokenWithoutDefaultRoom: "Your message did not contain any valid token. Nobody will read your message.",
        # (optional) The message, that will be sent to a matrix room, when sending a bridged message via SMS failed.
        sendSmsError: "Could not send SMS to this user. Please try it later again."
        # (optional) The content of bridged SMS message into the default room. Valid placeholders are {sender} and {body}.
        defaultRoomIncomingMessage: "{sender} wrote:\n{body}"
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
      regex: "@sms_[0-9]{6,15}:yourHomeServerDomain"
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
          # (optional) Path to the Gammu-Inbox directory. Default is "/var/spool/gammu/inbox".
          inboxPath: "/var/spool/gammu/inbox"
          # (optional) Path to the directory, where to put processed messages. Default is "/var/spool/gammu/inbox_processed".
          inboxProcessedPath: "/var/spool/gammu/inbox_processed"
```

Your `gammu-smsdrc` should look like this:
```text
[gammu]
Device = /dev/ttyModem
LogFile = /var/log/gammu/gammu.log
debugLevel = 1

[smsd]
Service = files
LoopSleep = 3
InboxPath = /var/spool/gammu/inbox/
OutboxPath = /var/spool/gammu/outbox/
SentSMSPath = /var/spool/gammu/sent/
ErrorSMSPath = /var/spool/gammu/error/
InboxFormat = detail
OutboxFormat = detail
TransmitFormat = auto
debugLevel = 1
LogFile = /var/log/gammu/smsd.log
DeliveryReport = log
DeliveryReportDelay = 7200
HangupCalls = 1
CheckBattery = 0
```

### Using Docker container
As long as there is only one Provider, Gammu is integrated into the Docker-container: `docker pull folivonet/matrix-sms-bridge`

To see, how a docker setup of the bridge could look like, have a look at the [example](./examples/gammu).