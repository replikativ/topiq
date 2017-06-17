(ns topiq.core
  (:require [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [clojure.data :refer [diff]]
            [clojure.set :as set]
            [datascript.core :as d]
            [hasch.core :refer [uuid]]
            [kabel.middleware.block-detector :refer [block-detector]]
            #_[kabel-auth.core :refer [auth]]
            [kioo.core :refer [handle-wrapper]]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [konserve.core :as k]
            [konserve.memory :refer [new-mem-store]]
            [om.core :as om :include-macros true]
            [om.dom :as omdom]
            [replikativ.crdt.cdvcs.realize :refer [stream-into-identity!]]
            [replikativ.crdt.cdvcs.stage :as sc]
            [replikativ.crdt.materialize :refer [key->crdt]]
            [replikativ.crdt.cdvcs.realize :refer [head-value]]
            [replikativ.crdt :refer [map->CDVCS]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.p2p.hooks :refer [hook default-integrity-fn]]
            [replikativ.peer :refer [client-peer]]
            [replikativ.protocols :refer [-downstream]]
            [replikativ.stage :as s]
            [topiq.view :refer [topiqs navbar topiq-arguments]]
            [superv.async :refer [S]]
            [taoensso.timbre :as timbre])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [superv.async :refer [<<? <? go-try go-loop-try alt? >?]]))

(enable-console-print!)

(def uri (goog.Uri. js/location.href))

(def cdvcs-id #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5")

(def ssl? (= (.getScheme uri) "https"))

(def ^:dynamic eval-fn {'(fn [S old params] (d/db-with old params))
                        (fn [S old params]
                          (when-not @old
                            (let [schema {:identity/id {:db/unique :db.unique/identity}}
                                  conn   (d/create-conn schema)]
                              (reset! old @conn)))
                          (swap! old d/db-with params)
                          old)
                        ;; keep old pre 0.2.4 schema
                        '(fn [old params] (d/db-with old params))
                        (fn [S old params]
                          (when-not @old
                            (let [schema {:identity/id {:db/unique :db.unique/identity}}
                                  conn   (d/create-conn schema)]
                              (reset! old @conn)))
                          (swap! old d/db-with params)
                          old)})


(defn navbar-view
  "Builds navbar with search, user menu and user-related modals"
  [login-fn app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:current-user "Not logged in"
       :search-placeholder "Search..."
       :search-text ""
       :login-user-text ""
       :login-fn login-fn})
    om/IRenderState
    (render-state [this {:keys [current-user search-text login-user-text search-placeholder] :as state}]
      (navbar
       owner
       state))))


(defn topiqs-view
  "Builds topiqs list with topiq head and related argument list, resolves conflicts"
  [stage app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-topiq false
       :stage stage
       ;; refresh time calculations
       :refresh-loop (go-loop []
                       (<! (timeout (* 60 1000)))
                       ;; do not eat the battery
                       (when-not (.-hidden js/document)
                         (om/refresh! owner))
                       (recur))})
    om/IRenderState
    (render-state [this {:keys [selected-topiq stage] :as state}]
      (let [user (get-in @stage [:config :user])
            val (om/value app)]
        (cond (= (type val) replikativ.crdt.cdvcs.realize/Conflict) ;; TODO implement with protocol dispatch
              (do
                (sc/merge! stage [user cdvcs-id] (:heads val))
                (omdom/h3 nil (str "Resolving conflicts... please wait. ")))

              (isa? (type val) js/Error)
              (omdom/div {:style "color:red"} (pr-str val))

              :else
              (if selected-topiq
                (topiq-arguments val owner)
                (topiqs val owner)))))))



;; necessary only for initial read below
(read/register-tag-parser! 'replikativ.crdt.CDVCS map->CDVCS)


(defonce state (atom {}))

(defn ^:export main [full-user & args]
  (go-try S
   (let [[[_ _ track-user]] (re-seq #"(.+):(.+)" full-user)
         val-atom (atom nil)
         login-fn (fn [stage hooks stream new-user]
                    (go-try S
                     ;; NOTE: always track eve to ensure convergence
                     (swap! stream (fn [s-ch] (close! (:close-ch s-ch))
                                     (stream-into-identity! stage [new-user cdvcs-id] eval-fn val-atom)))
                     (swap! hooks assoc ["mail:eve@topiq.es" cdvcs-id]
                            [[new-user cdvcs-id]
                             default-integrity-fn true])
                     (swap! hooks assoc [full-user cdvcs-id]
                            [[new-user cdvcs-id]
                             default-integrity-fn true])
                     (swap! stage assoc-in [:config :user] new-user)
                     (<? S (sc/fork! stage [full-user cdvcs-id]))))


         store
         ;; initialize the store in memory here for development processes
         ;; we need to defer record instantiation until cljs load time,
         ;; otherwise datascript and CDVCS are undefined for the clj
         ;; analyzer

         ;; NOTE: this automagically works with the server as well, as the
         ;; store is synchronized on connection
         (<? S (new-mem-store))
         hooks (atom {})
         uri-str (str
                  (if ssl?  "wss://" "ws://")
                  (.getDomain uri)
                  ;; allow local figwheel (port 3449) + server (8080) configuration
                  (when-let [port (if (= (.getDomain uri) "localhost") 8080 (.getPort uri))]
                    (str ":" port))
                  "/replikativ/ws")
         trusted-hosts (atom #{(.getDomain uri) :replikativ.stage/stage})
         receiver-token-store (<? S (new-mem-store))
         sender-token-store (<? S (new-mem-store))
         peer (<? S (client-peer S store 
                                 :middleware (comp (partial block-detector :client-core)
                                                   fetch
                                                   (partial hook hooks)
                                                   #_(partial auth
                                                          trusted-hosts
                                                          receiver-token-store
                                                          sender-token-store
                                                          (fn [{:keys [type]}]
                                                            (or ({:pub/downstream :auth} type)
                                                                :unrelated))
                                                          (fn [protocol user]
                                                            (go-try
                                                             (when-not (= user track-user)
                                                               (js/alert (pr-str "Check your inbox:" protocol user)))))
                                                          (fn [{:keys [user token protocol]}]
                                                            (when-not (= user track-user)
                                                              (.warn js/console "Cannot emit authentication requests from the browser. This should never happen! Please open a bug report!"))))
                                                 (partial block-detector :client-surface))))
         stage (<? S (s/create-stage! full-user peer))
         stream (atom (stream-into-identity! stage [full-user cdvcs-id] eval-fn val-atom))]

     (<? S (sc/create-cdvcs! stage :id cdvcs-id))
     ;; comment this out if you want to develop offline, e.g. with figwheel
     (s/connect! stage uri-str)

     (om/root
      (partial navbar-view (partial login-fn stage hooks stream))
      nil
      {:target (. js/document (getElementById "collapsed-navbar-group"))})

     (om/root
      (partial topiqs-view stage)
      val-atom
      {:target (. js/document (getElementById "topiq-container"))})

     ;; capture state for REPL
     (reset! state {:val-atom val-atom
                    :hooks hooks
                    :stream stream
                    :store store
                    :stage stage}))))




;; debug
(defn ^:export print-store []
  (let [{:keys [store]} @state]
    (println (pr-str store))))


;; HACK prototype helpers to reimport DB on schema changes etc.
;; TODO serialize datascript/DB properly with incognito
(defn ^:export print-db []
  (let [{:keys [val-atom]} @state]
    (println (pr-str @val-atom))))

(defn ^:export read-db [db-str]
  (let [{:keys [stage]} @state]
    (go-try S
     (.info js/console (<? S (sc/transact! stage ["mail:eve@topiq.es" cdvcs-id]
                                           [['(fn [S old params] (d/db-with old params))
                                             (mapv (fn [datom] (into [:db/add] datom))
                                                   (read-string db-str))]]))))))


(comment
  ;; jack in figwheel cljs REPL
  (require 'figwheel-sidecar.repl-api)
  (figwheel-sidecar.repl-api/cljs-repl)

  (get-in @(:state (:store @state)) [["mail:eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"] :state])
  (dissoc (get-in @stage ["mail:eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state]) :store)

  (dissoc (get-in @stage ["foo@bar.com" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state]) :store)

  (get-in @stage ["banana@joe.com" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state :heads])

  (get-in @stage [:config])



  ;; recreate database
  (sc/create-cdvcs! (:stage @state)
                    :id #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                    :description "topiq discourse.")
  (let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                :down-votes {:db/cardinality :db.cardinality/many}
                :posts {:db/cardinality :db.cardinality/many}
                :arguments {:db/cardinality :db.cardinality/many}
                :hashtags {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go
      (<! (sc/transact! (:stage @state) ["mail:eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                        [['(fn [_ new] new)
                          @conn]]))))


  (go
    (let [start (js/Date.)]
      (doseq [i (range 1e1)]
        (<! (sc/transact! stage ["mail:eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                          [['(fn [old params] (d/db-with old params))
                            [{:db/unique-identity [:item/id (uuid)]
                              :title (str i)
                              :detail-text  (str i)
                              :author "benchmark@topiq.es"
                              :ts (js/Date.)}]]]))
        (<! (timeout 100)))
      (def benchmark (- (js/Date.) start))))

  (-> @stage :volatile :peer deref :volatile :store :state deref keys))


