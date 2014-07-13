(ns link-collective.core
  (:require [link-collective.view :refer [topiqs navbar comments]]
            [clojure.data :refer [diff]]
            [domina :as dom]
            [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [hasch.core :refer [uuid]]
            [datascript :as d]
            [geschichte.stage :as s]
            [geschichte.sync :refer [client-peer]]
            [geschichte.auth :refer [auth]]
            [konserve.store :refer [new-mem-store]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [om.dom :as omdom])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))

;; fire up repl
#_(do
    (ns weasel.startup)
    (require 'weasel.repl.websocket)
    (cemerick.piggieback/cljs-repl
        :repl-env (weasel.repl.websocket/repl-env
                   :ip "0.0.0.0" :port 17782)))


;; weasel websocket
#_(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
     :jsload-callback (fn [] (print "reloaded")))
    (ws-repl/connect "ws://localhost:17782" :verbose true)))


(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params]
                 (:db-after (d/transact old params)))
              (fn [old params]
                (:db-after (d/transact old params)))})



; we can do this runtime wide here, since we only use this datascript version
(read/register-tag-parser! 'datascript/DB datascript/db-from-reader)
(read/register-tag-parser! 'datascript/Datom datascript/datom-from-reader)



(defn navbar-view
  "Builds navbar with search, user menu and user-related modals"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:current-user "Not logged in"
       :search-placeholder "Search..."
       :search-text ""
       :login-user-text ""})
    om/IRenderState
    (render-state [this {:keys [current-user search-text login-user-text search-placeholder] :as state}]
      (navbar
       owner
       state))))


(defn topiqs-view
  "Builds topiqs list with topiq head and related comment list, resolves conflicts"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-topiq false
       :stage stage})
    om/IRenderState
    (render-state [this {:keys [selected-topiq] :as state}]
      (let [val (om/value (get-in app ["eve@polyc0l0r.net"
                                       #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                                       "master"]))]
        (cond (= (type val) geschichte.stage/Conflict) ;; TODO implement with protocol dispatch
              (do
                (s/merge! stage ["eve@polyc0l0r.net"
                                 #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                                 "master"]
                          (concat (map :id (:commits-a val))
                                  (map :id (:commits-b val))))
                (omdom/div nil (str "Resolving conflicts... please wait. " (pr-str val))))

              (= (type val) geschichte.stage/Abort) ;; reapply
              (do
                (s/transact stage ["eve@polyc0l0r.net"
                                   #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                                   "master"] (:aborted val))
                (omdom/div nil (str "Retransacting your changes on new value... " (:aborted val))))
              :else
              (if selected-topiq
                (comments app owner)
                (topiqs app owner)))))))


(def trusted-hosts (atom #{:geschichte.stage/stage (.getDomain uri)}))


(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@polyc0l0r.net" "lisp"}))


(go
  (def store
    (<! (new-mem-store
         ;; empty db
         (atom (read-string
                "{#uuid \"0343106c-fd31-55f0-ac48-eb1f92427160\" {:transactions [[#uuid \"197bf9d9-1edf-5a11-b4d9-e3ce09d58556\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-06-26T19:58:55.573-00:00\", :author \"eve@polyc0l0r.net\"}, #uuid \"197bf9d9-1edf-5a11-b4d9-e3ce09d58556\" #datascript/DB {:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :posts {:db/cardinality :db.cardinality/many}, :comments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :datoms []}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), \"eve@polyc0l0r.net\" {#uuid \"b09d8708-352b-4a71-a845-5f838af04116\" {:branches {\"master\" #{#uuid \"0343106c-fd31-55f0-ac48-eb1f92427160\"}}, :id #uuid \"b09d8708-352b-4a71-a845-5f838af04116\", :description \"link-collective discourse.\", :head \"master\", :last-update #inst \"2014-06-26T19:58:55.573-00:00\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :causal-order {#uuid \"0343106c-fd31-55f0-ac48-eb1f92427160\" []}, :public false, :pull-requests {}}}}"))
         (atom  {'datascript/Datom datascript/datom-from-reader
                 'datascript/DB datascript/db-from-reader}))))

  (def peer (client-peer "CLIENT-PEER"
                         store
                         (partial auth store auth-fn (fn [creds] nil) trusted-hosts)))

  (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

  (<! (s/subscribe-repos! stage
                          {"eve@polyc0l0r.net"
                           {#uuid "b09d8708-352b-4a71-a845-5f838af04116"
                            #{"master"}}}))

  ;; fix back to functions in production
  (<! (s/connect!
       stage
       (str
        (if ssl?  "wss://" "ws://")
        (.getDomain uri)
        (when (= (.getDomain uri) "localhost")
          (str ":" 8080 #_(.getPort uri)))
        "/geschichte/ws")))

  (om/root
   navbar-view
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "collapsed-navbar-group"))})

  (om/root
   topiqs-view
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "topiq-container"))}))



(comment
  ;; recreate database
  (let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                :down-votes {:db/cardinality :db.cardinality/many}
                :posts {:db/cardinality :db.cardinality/many}
                :comments {:db/cardinality :db.cardinality/many}
                :hashtags {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go
      (<! (s/create-repo! stage
                          "eve@polyc0l0r.net"
                          "link-collective discourse."
                          @conn
                          "master"))))


  (def eve-data (get-in @stage [:volatile :val-atom]))


)
