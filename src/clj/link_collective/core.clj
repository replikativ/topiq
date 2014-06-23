(ns link-collective.core
  (:gen-class :main true)
  (:require [clojure.edn :as edn]
            [net.cgrand.enlive-html :refer [deftemplate set-attr]]
            [clojure.java.io :as io]
            [compojure.route :refer [resources]]
            [compojure.core :refer [GET POST defroutes]]
            [geschichte.repo :as repo]
            [geschichte.stage :as s]
            [geschichte.meta :refer [update]]
            [geschichte.sync :refer [server-peer client-peer]]
            [geschichte.platform :refer [create-http-kit-handler!]]
            [konserve.store :refer [new-mem-store]]
            [konserve.platform :refer [new-couch-store]]
            [compojure.handler :refer [site api]]
            [org.httpkit.server :refer [with-channel on-close on-receive run-server send!]]
            [ring.util.response :as resp]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [clojure.core.async :refer [timeout sub chan <!! >!! <! >! go go-loop] :as async]
            [com.ashafa.clutch.utils :as utils]
            [com.ashafa.clutch :refer [couch]]
            [clojure.tools.logging :refer [info warn error]]))


(def server-state (atom nil))


(deftemplate static-page
  (io/resource "public/index.html")
  []
  [:#bootstrap-css] (set-attr "href" "static/bootstrap/bootstrap-3.1.1-dist/css/bootstrap.min.css")
  [:#bootstrap-theme-css] (set-attr "href" "static/bootstrap/bootstrap-3.1.1-dist/css/bootstrap-theme.min.css")
  [:#react-js] (set-attr "src" "static/react/react-0.9.0.min.js")
  [:#jquery-js] (set-attr "src" "static/jquery/jquery-1.11.0.min.js")
  [:#bootstrap-js] (set-attr "src" "static/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js"))


(defn create-store
  "Creates a konserve store"
  [state]
  (swap!
   state
   (fn [old new] (assoc-in old [:store] new))
   #_(<!! (new-mem-store))
   (<!! (new-couch-store
             (couch (utils/url (:couchdb-url @state) "link-collective"))
             (:tag-table @state))))
  state)


(defn create-peer
  "Creates geschichte server peer"
  [state]
  (swap!
   state
   (fn [old new] (assoc-in old [:peer] new))
   (server-peer
    (create-http-kit-handler!
     (str (if (= (:proto @state) "https") "wss" "ws")
          "://" (:host @state)
          ":" (:port @state)
          "/geschichte/ws")
     (:tag-table @state))
    (:store @state)))
  state)


(defn fetch-url [url]
  (enlive/html-resource (java.net.URL. url)))


(defn fetch-url-title [url]
  "fetch url and extract title"
  (-> (fetch-url url)
      (enlive/select [:head :title])
      first
      :content
      first))


(defn dispatch-bookmark
  "Dispatches websocket packages"
  [{:keys [topic data] :as incoming}]
  (case topic
    :greeting {:data "Greetings Master!" :topic :greeting}
    :fetch-title {:title (fetch-url-title (:url data))}
    "DEFAULT"))


(defn bookmark-handler
  "Reacts to incoming websocket packages"
  [request]
  (with-channel request channel
    (on-close channel
              (fn [status]
                (info "bookmark channel closed: " status)))
    (on-receive channel
                (fn [data]
                  (info (str "Incoming package: " (java.util.Date.)))
                  (send! channel (str (dispatch-bookmark (edn/read-string data))))))))


(defroutes handler
  (resources "/")

  (GET "/bookmark/ws" [] bookmark-handler) ;; websocket handling

  (GET "/geschichte/ws" [] (-> @server-state :peer deref :volatile :handler))

  (GET "/*" [] (if (= (:build @server-state) :prod)
                 (static-page)
                 (io/resource "public/index.html"))))


(defn read-config [state path]
  (let [config (-> path slurp read-string
                   (update-in [:couchdb-url] eval) ;; maybe something better but I don't want to deal withj system vars in here
                   (assoc :tag-table
                     (atom {'datascript.Datom
                            (fn [val] (info "DATASCRIPT-DATOM:" val)
                              (konserve.literals.TaggedLiteral. 'datascript.Datom val))})))]
    (swap! state merge config))
  state)


(defn init
  "Read in config file, create sync store and peer"
  [state path]
  (-> state
      (read-config path)
      create-store
      create-peer))


(defn start-server [port]
  (do
    (info (str "Starting server @ port " port))
    (run-server (site #'handler) {:port port :join? false})))


(defn -main [& args]
  (init server-state (first args))
  (info (clojure.pprint/pprint @server-state))
  (start-server (:port @server-state)))


(comment

  (init server-state "resources/server-config.edn")

  (def server (start-server (:port @server-state)))

  (server)

)
