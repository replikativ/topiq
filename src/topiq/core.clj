(ns topiq.core
  (:gen-class :main true)
  (:require [clojure.core.async :refer [timeout sub chan <! >!! >! go go-loop] :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.tools.logging :refer [info warn error]]
            [ring.util.response :as resp]
            [compojure.core :refer [GET POST defroutes]]
            [compojure.handler :refer [site api]]
            [compojure.route :refer [resources]]
            [kabel.middleware.block-detector :refer [block-detector]]
            [konserve.core :as k]
            [konserve.filestore :refer [new-fs-store]]
            [konserve.memory :refer [new-mem-store]]
            [net.cgrand.enlive-html :refer [deftemplate set-attr substitute html] :as enlive]
            [org.httpkit.server :refer [run-server]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.p2p.hash :refer [ensure-hash]]
            [replikativ.p2p.hooks :refer [hook]]
            [replikativ.peer :refer [server-peer client-peer]]
            [replikativ.stage :as s]
            [full.async :refer [<?? <? go-try go-loop-try]]))

(deftemplate static-page
  (io/resource "public/index.html")
  []
  [:#bootstrap-css] (set-attr "href" "static/bootstrap/bootstrap-3.1.1-dist/css/bootstrap.min.css")
  [:#bootstrap-theme-css] (set-attr "href" "static/bootstrap/bootstrap-3.1.1-dist/css/bootstrap-theme.min.css")
  [:#react-js] (set-attr "src" "static/react/react-0.9.0.min.js")
  [:#jquery-js] (set-attr "src" "static/jquery/jquery-1.11.0.min.js")
  [:#bootstrap-js] (set-attr "src" "static/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js")
  [:#js-files] (substitute (html [:script {:src "js/main.js" :type "text/javascript"}])))

(def config (-> "resources/server-config.edn" slurp read-string))

(def store (<?? (new-mem-store) #_(new-fs-store "store")))

(def hooks (atom (or (:hooks config) {})))

(def err-ch (chan))

(go-loop [e (<! err-ch)]
  (when e
    (println "TOPIQ UNCAUGHT" e)
    (recur (<! err-ch))))

(def peer (let [{:keys [proto host port build trusted-hosts]} config
                uri (str (if (= proto "https") "wss" "ws") ;; should always be wss with auth
                         "://" host (when (= :dev build) (str ":" port)) "/replikativ/ws")]
            (<?? (server-peer store err-ch uri
                              :middleware (comp (partial block-detector :server)
                                                (partial fetch store (atom {}) err-ch)
                                                (partial hook hooks store)
                                                ensure-hash)))))

(def stage (<?? (s/create-stage! (:user config) peer err-ch)))

(doseq [url (:connect config)]
  (s/connect! stage url))

(defroutes handler
  (resources "/")

  (GET "/replikativ/ws" [] (-> @peer :volatile :handler))

  (GET "/*" [] (if (= (:build config) :prod)
                 (static-page)
                 (io/resource "public/index.html"))))

(defn -main [& args]
  (let [{:keys [port]} config]
    (info (str "Starting server @ port " port))
    (def server (run-server (site #'handler) {:port port :join? false}))))


(comment
  (<?? (k/get-in store [:peer-config]))

  (def commit-eval {'(fn [_ new] new) (fn replace [old params] [params])
                    '(fn [old params] (d/db-with old params)) (fn [old params] (conj old params))})

  (require '[replikativ.crdt.cdvcs.realize :refer [commit-value commit-history-values]])

  (<?? (commit-history-values store
                              (:commit-graph (:state (<?? (k/get-in
                              store [["mail:eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]]))))
                              #uuid "1699bb60-2ba4-5be1-908f-e00a03cfeef4"))



  )
