(defproject link-collective "0.1.0-SNAPSHOT"

  :description "Link collection again"

  :url "https://github.com/kordano/link-collective"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.0.0"

  :source-paths ["src/cljs" "src/clj"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2227"]
                 [om "0.6.3"]
                 [figwheel "0.1.3-SNAPSHOT"]
                 [kioo "0.4.0"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-figwheel "0.1.3-SNAPSHOT"]
            [lein-ancient "0.5.4"]]

  :figwheel {:http-server-root "public"
             :port 3449
             :css-dirs ["resources/public/css"]}

  :cljsbuild {:builds
              [{:source-paths ["src/cljs"]
                :compiler
                {:output-to "resources/public/js/compiled/main.js"
                 :output-dir "resources/public/js/compiled/out"
                 :optimizations :none
                 :source-map true}}]})
