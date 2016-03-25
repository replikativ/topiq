(defproject topiq "0.1.0-SNAPSHOT"

  :description "An app to collectively link and share data."

  :url "https://github.com/kordano/topiq"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.cache "0.6.4"]
                 ;; implicitly needed?
                 [org.clojure/core.memoize "0.5.8" :exclusions [org.clojure/core.cache]]
                 [org.clojure/core.async "0.2.374"]

                 [com.fzakaria/slf4j-timbre "0.3.1"]

                 [ring "1.4.0"] ;; implicitly needed?
                 [com.cemerick/friend "0.2.1"]
                 [enlive "1.1.6"]
                 [compojure "1.4.0"]

                 [domina "1.0.3"]
                 [datascript "0.15.0"]
                 [org.omcljs/om "0.9.0"]
                 [kioo "0.4.1"]
                 [io.replikativ/incognito "0.2.0-SNAPSHOT"] ;; TODO why is this necessary?
                 [http.async.client "0.6.1"] ;; revert: allows bigger frame size for now
                 [io.replikativ/replikativ "0.1.4-SNAPSHOT"]
                 [io.replikativ/kabel-auth "0.1.0-SNAPSHOT"]
                 [com.draines/postal "1.11.3"]
                 [markdown-clj "0.9.82"]]


  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]]
                   :figwheel {:nrepl-port 7888
                              :nrepl-middleware ["cider.nrepl/cider-middleware"
                                                 "cemerick.piggieback/wrap-cljs-repl"]}
                   :plugins [[lein-figwheel "0.5.0-2"]]}
             :uberjar {:aot :all}}

  :plugins [[lein-cljsbuild "1.1.2"]]

  :main topiq.core

  :prep-tasks ["compile" ["cljsbuild" "once" "prod"]]

  :uberjar-name "topiq-standalone.jar"


  :figwheel {:http-server-root "public"
             :port 3449
             :css-dirs ["resources/public/css"]}

  :clean-targets ^{:protect false} ["target" "resources/public/js"]

  :cljsbuild
  {:builds
   {:figwheel {:source-paths ["src/"]
               :figwheel true
               :compiler
               {:main topiq.core
                :asset-path "js/out"
                :output-to "resources/public/js/main.js"
                :output-dir "resources/public/js/out"
                :optimizations :none
                :pretty-print true}}
    :dev {:source-paths ["src"]
          :compiler
          {:main topiq.core
           :output-to "resources/public/js/main.js"
           :optimizations :simple
           :pretty-print true}}
    :prod {:source-paths ["src"]
           :compiler
           {:main topiq.core
            :output-to "resources/public/js/main.js"
            :optimizations :advanced}}}})
