(defproject link-collective "0.1.0-SNAPSHOT"

  :description "An app to collectively link and share data."

  :url "https://github.com/kordano/link-collective"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]

                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.slf4j/slf4j-log4j12 "1.7.6"]
                 [org.clojure/tools.logging "0.2.6"]

                 [ring "1.2.2"]
                 [com.cemerick/friend "0.2.0"]
                 [com.ashafa/clutch "0.4.0-RC1"]
                 [enlive "1.1.5"]
                 [compojure "1.1.6"]

                 [datascript "0.1.4"]
                 [om "0.5.0"]
                 [kioo "0.4.0"]
                 [http-kit "2.1.18"]
                 [com.facebook/react "0.9.0.1"]
                 [net.polyc0l0r/geschichte "0.1.0-SNAPSHOT"]

                 [weasel "0.2.0"]]

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :plugins [[lein-cljsbuild "1.0.3"]]

  :cljsbuild
  {:builds
   [{:source-paths ["src/cljs"]
     :compiler
     {:output-to "resources/public/js/main.js"
      :optimizations :simple}}]})
