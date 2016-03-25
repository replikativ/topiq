(ns topiq.core
  (:gen-class :main true)
  (:require [clojure.core.async :refer [timeout sub chan <! >!! >!
                                        go go-loop put! close!] :as async]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [kabel.platform-log :refer [info warn error debug]]
            [ring.util.response :as resp]
            [compojure.core :refer [GET POST routes]]
            [compojure.handler :refer [site api]]
            [compojure.route :refer [resources]]
            [kabel.middleware.block-detector :refer [block-detector]]
            [kabel-auth.core :refer [auth inbox-auth register-external-token external-tokens]]
            [kabel.platform :as plat]
            [konserve.core :as k]
            [konserve.filestore :refer [new-fs-store]]
            [konserve.memory :refer [new-mem-store]]
            [net.cgrand.enlive-html :refer [deftemplate set-attr substitute html] :as enlive]
            [org.httpkit.server :refer [run-server]]
            [postal.core :refer [send-message]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.p2p.hash :refer [ensure-hash]]
            [replikativ.p2p.hooks :refer [hook]]
            [replikativ.peer :refer [server-peer client-peer]]
            [replikativ.stage :as s]
            [full.async :refer [<?? <? go-try go-loop-try]]))

;; handle unhandled errors
(def err-ch (chan))

(go-loop [e (<! err-ch)]
  (when e
    (println "TOPIQ UNCAUGHT" e)
    (recur (<! err-ch))))

;; handle authentication requests
(defn auth-handler [{:keys [proto host port build mail-config]}
                    {:keys [protocol token user]}]
  (let [ext-tok (register-external-token token)
        body (str "Please visit: " proto "://"
                  host (when (= build :dev) (str ":" port))
                  "/auth/" ext-tok " to complete the authencation.")]
    (if-not (= protocol :mail)
      (warn "Cannot handle protocol" protocol " for " user)
      (try
        (debug "mailing:" user body)
        (send-message mail-config
                      {:from (str "no-reply@" host)
                       :to user
                       :subject (str "Please authenticate on " host)
                       :body body})
        (catch Exception e
          (debug "Could not send mail to " user ":" e))))))

(defn auth-token [ext-token]
  (if-let [token (@external-tokens ext-token)]
    (do
      (put! inbox-auth {:token token})
      (str "You have been authenticated on this peer."))
    (do (str "Could find your authentication request.")
        (debug "Missed authentication for: " ext-token))))

;; setup static page
(deftemplate static-page
  (io/resource "public/index.html")
  []
  [:#bootstrap-css] (set-attr "href" "static/bootstrap/bootstrap-3.1.1-dist/css/bootstrap.min.css")
  [:#bootstrap-theme-css] (set-attr "href" "static/bootstrap/bootstrap-3.1.1-dist/css/bootstrap-theme.min.css")
  [:#react-js] (set-attr "src" "static/react/react-0.9.0.min.js")
  [:#jquery-js] (set-attr "src" "static/jquery/jquery-1.11.0.min.js")
  [:#bootstrap-js] (set-attr "src" "static/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js")
  [:#js-files] (substitute (html [:script {:src "js/main.js" :type "text/javascript"}])))

(defn start [{:keys [proto host port user build
                     trusted-hosts connect hooks mail-config] :as config}]
  (let [store (<?? #_(new-mem-store) (new-fs-store "store"))
        hooks (atom (or hooks {}))
        trusted-hosts (atom trusted-hosts)
        sender-token-store (<?? (new-mem-store))
        receiver-token-store (<?? (new-mem-store))
        uri (str (if (= proto "https") "wss" "ws") ;; should always be wss with auth
                 "://" host (when (= build :dev) (str ":" port)) "/replikativ/ws")
        peer (<?? (server-peer store err-ch uri
                               :middleware (comp (partial block-detector :server)
                                                 (partial fetch store (atom {}) err-ch)
                                                 (partial hook hooks store)
                                                 (partial auth
                                                          trusted-hosts
                                                          receiver-token-store
                                                          sender-token-store
                                                          (fn [{:keys [type]}]
                                                            (or ({:pub/downstream :auth} type)
                                                                :unrelated))
                                                          #(warn "Unhandled auth triggered:" (pr-str %))
                                                          (partial auth-handler config))
                                                 ensure-hash)))
        handler (routes
                 (resources "/")
                 (GET "/replikativ/ws" [] (-> @peer :volatile :handler))
                 (GET "/auth/:token" [token] (auth-token (java.util.UUID/fromString token)))
                 (GET "/*" [] (if (= build :prod) (static-page) (io/resource "public/index.html"))))
        stage (<?? (s/create-stage! "none:dummy@localhost" peer err-ch))]

    #_(doseq [url connect]
      (s/connect! stage url))

    {:store store
     :trusted-hosts trusted-hosts
     :sender-token-store sender-token-store
     :receiver-token-store receiver-token-store
     :hooks hooks
     :peer peer
     :stage stage
     :config config
     :server (run-server (site handler) {:port port :join? false})}))

(defn stop [{:keys [peer server]}]
  (plat/stop peer)
  (server))


(defn -main [path]
  (let [{:keys [port] :as config} (-> (or path "resources/server-config.edn")
                                      slurp
                                      read-string)]
    (info (str "Starting server @ port " port))
    (def state (start config))))

(comment
  (-main "resources/server-config.edn")
  (stop state)

  (:config state)


  (let [host "localhost"
        proto "http"
        port "8080"
        ext-tok "TOK"
        build :dev]
    (send-message {:host "smtp.topiq.es"}
                  {:from  (str "no-reply@" host)
                   :to "whilo@topiq.es"
                   :subject (str "Please authenticate on " host)
                   :body (str "Please visit: " proto "://"
                              host (when (= :dev build) (str ":" port))
                              "/auth/" ext-tok " to complete the authencation.")}))


  (<?? (k/get-in store [:peer-config]))

  (def commit-eval {'(fn [_ new] new) (fn replace [old params] [params])
                    '(fn [old params] (d/db-with old params)) (fn [old params] (conj old params))})

  (require '[replikativ.crdt.cdvcs.realize :refer [commit-value commit-history-values]])

  (<?? (commit-history-values store
                              (:commit-graph (:state (<?? (k/get-in
                                                           store [["mail:eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]]))))
                              #uuid "1699bb60-2ba4-5be1-908f-e00a03cfeef4"))

  )
