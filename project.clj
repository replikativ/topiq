(defproject topiq "0.1.0-SNAPSHOT"

  :description "An app to collectively link and share data."

  :url "https://github.com/kordano/topiq"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]
                 [org.clojure/core.cache "0.6.5"]
                 ;; implicitly needed?
                 [org.clojure/core.memoize "0.5.9" :exclusions [org.clojure/core.cache]]

                 [com.fzakaria/slf4j-timbre "0.3.2"]

                 [ring "1.5.0"] ;; implicitly needed?
                 [ring-cors "0.1.8"]
                 [enlive "1.1.6"]
                 [compojure "1.5.1"]

                 [domina "1.0.3"]
                 [datascript "0.15.4"]
                 [org.omcljs/om "0.9.0"]
                 [kioo "0.4.2"]

                 [io.replikativ/replikativ "0.2.2"]
                 #_[io.replikativ/kabel-auth "0.1.0-SNAPSHOT"]
                 [com.draines/postal "2.0.1"]
                 [markdown-clj "0.9.90"]]


  :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]]
                   :figwheel {:nrepl-port 7888
                              #_:nrepl-middleware #_["cider.nrepl/cider-middleware"
                                                 "cemerick.piggieback/wrap-cljs-repl"]}
                   :plugins [[lein-figwheel "0.5.8"]]}
             :uberjar {:aot :all}}

  :plugins [[lein-cljsbuild "1.1.4"]]

  :main topiq.core

  :prep-tasks ["compile" ["cljsbuild" "once" "prod"]]

  :uberjar-name "topiq-standalone.jar"


  :figwheel {:http-server-root "public"
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
           :output-to "resources/public/js/dev/main.js"
           :output-dir "resources/public/js/dev/out"
           :optimizations :none
           :pretty-print true}}
    :prod {:source-paths ["src"]
           :compiler
           {:main topiq.core
            :output-to "resources/public/js/prod/main.js"
            :output-dir "resources/public/js/prod/out"
            :optimizations :advanced}}}})
