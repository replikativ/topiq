(ns topiq.core
  (:require [topiq.view :refer [topiqs navbar topiq-arguments]]
            [clojure.data :refer [diff]]
            [domina :as dom]
            [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [hasch.core :refer [uuid]]
            [datascript :as d]
            [geschichte.stage :as s]
            [geschichte.sync :refer [client-peer]]
            [konserve.store :refer [new-mem-store]]
            [geschichte.p2p.auth :refer [auth]]
            [geschichte.p2p.fetch :refer [fetch]]
            [geschichte.p2p.publish-on-request :refer [publish-on-request]]
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
(if (= "localhost" (.getDomain uri))
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
  "Builds topiqs list with topiq head and related argument list, resolves conflicts"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-topiq false
       :stage stage})
    om/IRenderState
    (render-state [this {:keys [selected-topiq] :as state}]
      (let [val (om/value (get-in app ["eve@polyc0l0r.net"
                                   #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                                       "master"]))]
        (cond (= (type val) geschichte.stage/Conflict) ;; TODO implement with protocol dispatch
              (do
                (s/merge! stage ["eve@polyc0l0r.net"
                                   #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                                 "master"]
                          (concat (map :id (:commits-a val))
                                  (map :id (:commits-b val))))
                (omdom/div nil (str "Resolving conflicts... please wait. " (pr-str val))))

              (= (type val) geschichte.stage/Abort) ;; reapply
              (do
                (s/transact stage ["eve@polyc0l0r.net"
                                   #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                                   "master"] (:aborted val))
                (omdom/div nil (str "Retransacting your changes on new value... " (:aborted val))))
              :else
              (if selected-topiq
                (topiq-arguments app owner)
                (topiqs app owner)))))))


(def trusted-hosts (atom #{:geschichte.stage/stage (.getDomain uri)}))


(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@polyc0l0r.net" "lisp"}))


(go
  (def store
    (<! (new-mem-store
         (atom (read-string
                "{#uuid \"23c147d1-35d5-5ae3-bfcf-50ac151f6bba\" #datascript/DB {:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :posts {:db/cardinality :db.cardinality/many}, :arguments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :datoms []}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), #uuid \"2129d0b6-8e6d-5917-962a-2dde8dde3e86\" {:transactions [[#uuid \"23c147d1-35d5-5ae3-bfcf-50ac151f6bba\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-07-14T09:48:49.199-00:00\", :author \"eve@polyc0l0r.net\"}, \"eve@polyc0l0r.net\" {#uuid \"26558dfe-59bb-4de4-95c3-4028c56eb5b5\" {:description \"topiq discourse.\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :pull-requests {}, :causal-order {#uuid \"2129d0b6-8e6d-5917-962a-2dde8dde3e86\" []}, :public false, :branches {\"master\" #{#uuid \"2129d0b6-8e6d-5917-962a-2dde8dde3e86\"}}, :head \"master\", :last-update #inst \"2014-07-14T09:48:49.199-00:00\", :id #uuid \"26558dfe-59bb-4de4-95c3-4028c56eb5b5\"}}}")
               (atom {'datascript/Datom datascript/datom-from-reader
                 'datascript/DB datascript/db-from-reader})))))

  (def peer (client-peer "CLIENT-PEER"
                         store
                         (comp (partial publish-on-request store)
                               (partial fetch store))))

  (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

  (<! (s/subscribe-repos! stage
                          {"eve@polyc0l0r.net"
                           {#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
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
                :arguments {:db/cardinality :db.cardinality/many}
                :hashtags {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go
      (println (<! (s/create-repo! stage
                                   "eve@polyc0l0r.net"
                                   "topiq discourse."
                                   @conn
                                   "master")))))

  (-> @stage :volatile :peer deref :volatile :store :state deref)

)
