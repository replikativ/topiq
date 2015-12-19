(defproject topiq "0.1.0-SNAPSHOT"

  :description "An app to collectively link and share data."

  :url "https://github.com/kordano/link-collective"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.189"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/core.memoize "0.5.8" :exclusions [org.clojure/core.cache]]
                 [org.clojure/core.async "0.2.374"]

                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms
                                                    com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [org.slf4j/slf4j-log4j12 "1.7.13"]
                 [org.clojure/tools.logging "0.3.1"]

                 [http-kit "2.1.19"]
                 [ring "1.4.0"]
                 [com.cemerick/friend "0.2.1"]
                 [enlive "1.1.6"]
                 [compojure "1.4.0"]

                 [domina "1.0.3"]
                 [datascript "0.13.3"]
                 #_[com.facebook/react "0.12.2.4"]
                 [org.omcljs/om "0.9.0"]
                 [kioo "0.4.1"]
                 [io.replikativ/replikativ "0.1.0-SNAPSHOT"]
                 [markdown-clj "0.9.82"]]

  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]]
                   :figwheel {:nrepl-port 7888
                              :nrepl-middleware ["cider.nrepl/cider-middleware"
                                                 "cemerick.piggieback/wrap-cljs-repl"]}
                   :plugins [[lein-figwheel "0.5.0-2"]]}}

  :plugins [[lein-cljsbuild "1.1.1"]]

  :main topiq.core

  :uberjar-name "topiq-standalone.jar"


  :figwheel {:http-server-root "public"
             :port 3449
             :css-dirs ["resources/public/css"]}

  :clean-targets ^{:protect false} ["target" "resources/public/js"]

  :cljsbuild
  {:builds
   [{:id "cljs_repl"
     :source-paths ["src/"]
     :figwheel true
     :compiler
     {:main topiq.core
      :asset-path "js/out"
      :output-to "resources/public/js/main.js"
      :output-dir "resources/public/js/out"
      :optimizations :none
      :pretty-print true}}
    {:id "dev"
     :source-paths ["src"]
     :compiler
     {:main topiq.core
      :output-to "resources/public/js/main.js"
      :optimizations :simple
      :pretty-print true}}]}


  )
