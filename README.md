# Topiq

A social network application based on [P2P replication with CRDTs](https://github.com/replikativ/replikativ).

Test p2p network at:
- <https://topiq.es>.

## Usage

You can use the app in your browser all state changes are
automatically synchronized and topiq reconnects automatically after
you have been offline. Sometimes conflicts can happen and are resolved
automatically atm. This will probably change at some point depending
on the future feature set.

## Deployment <a href="https://gitter.im/replikativ/replikativ?utm_source=badge&amp;utm_medium=badge&amp;utm_campaign=pr-badge&amp;utm_content=badge"><img src="https://camo.githubusercontent.com/da2edb525cde1455a622c58c0effc3a90b9a181c/68747470733a2f2f6261646765732e6769747465722e696d2f4a6f696e253230436861742e737667" alt="Gitter" data-canonical-src="https://badges.gitter.im/Join%20Chat.svg" style="max-width:100%;"></a>

We will be more than happy if you join the test network! Just drop into
the replikativ channel of gitter and say hello :)

Atm. you need an SMTP server which can send messages to notify users
about authentication requests.

Edit the config settings:

~~~clojure
{:build :dev ;; or :prod
 :behind-proxy false
 :proto "http"
 :port 8080
 :host "localhost" ;; hostname of the web-frontend
 ;; only extend this for CDVCS' you control and do it on one peer
 ;; to avoid conflict management! atm. one peer pulls into the global
 ;; CDVCS for eve
 ;; :hooks {}
 :connect ["wss://topiq.es/replikativ/ws"
           "wss://more.topiq.es/replikativ/ws" ]
 ;; supplied to the library postal https://github.com/drewr/postal
 ;; have a look at their docs for details:
 :mail-config {:host "smtp.your-isp.com"}
 ;; peers you trust and which need no authentication to promote state changes
 :trusted-connections #{"wss://topiq.es/replikativ/ws"
                        "wss://more.topiq.es/replikativ/ws"}}
~~~

Build an `AOT`-compiled jar file:

~~~bash
git clone https://github.com/whilo/topiq
lein uberjar # also compiles advanced cljs
java -jar target/topiq-standalone.jar resources/server-config.edn
~~~

## TODO

- build test network
- better editor for markdown
- improve datascript queries and understanding, e.g. rank & sort in datascript
- make layout embeddable as comment stream
- organized in a discourse/conversation (branch?), e.g. private
  conversation "discourse" for each friend as "messaging"
- support search by hash-tag, user
- plugins to add data and structure to comments / social apps
- individual up to collective help to summarize for new topic

## License

Copyright © 2014-2016 Christian Weilbach
Copyright © 2014-2015 Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
