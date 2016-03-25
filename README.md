# Topiq

A social network application based on [P2P replication with CRDTs](https://github.com/replikativ/replikativ).

Test p2p network at:
- <https://topiq.es>
- <http://topiq.polyc0l0r.net:8080> (can only auth USER@topiq.es atm.)

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
 :host "localhost" ;; adjust hostname
 :user "mail:eve@your-isp.com"
 ;; only do this for CDVCS' you control and do it on one peer
 ;; to avoid server-side conflict management!
 :hooks {[#".*"
          #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
         [["mail:eve@your-isp.com"
           #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]]}
 :connect ["wss://topiq.es/replikativ/ws"]
 :mail-config {:host "smtp.your-isp.com"}
 :trusted-hosts #{"topiq.es" "78.47.61.129"}
 }
~~~

Build an `AOT`-compiled jar file:

~~~bash
git clone https://github.com/whilo/topiq
lein uberjar # also compiles advanced cljs
java -jar target/topiq-standalone.jar resources/server-config.edn
~~~

You probably also have to add the letsencrypt tool chain to your JDK
with keytool. You can see this when you get connection errors with ssl
for topiq.es.

~~~bash
wget https://letsencrypt.org/certs/isrgrootx1.pem
wget https://letsencrypt.org/certs/letsencryptauthorityx1.der
sudo keytool -trustcacerts -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit -noprompt -importcert -alias isrgrootx1 -file ~/isrgrootx1.pem
sudo keytool -trustcacerts -keystore $JAVA_HOME/jre/lib/security/cacerts -storepass changeit -noprompt -importcert -alias letsencryptauthorityx1 -file ~/letsencryptauthorityx1.der
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
