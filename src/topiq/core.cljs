(ns topiq.core
  (:require [topiq.view :refer [topiqs navbar topiq-arguments]]
            [clojure.data :refer [diff]]
            [hasch.core :refer [uuid]]
            [datascript.core :as d]
            [replikativ.stage :as s]
            [replikativ.crdt.cdvcs.realize :refer [commit-history-values trans-apply summarize-conflict]]
            [replikativ.crdt.cdvcs.stage :as sc]
            [replikativ.crdt.materialize :refer [key->crdt]]
            [replikativ.peer :refer [client-peer]]
            [replikativ.crdt :refer [map->CDVCS]]
            [konserve.memory :refer [new-mem-store]]
            [konserve.core :as k]
            [replikativ.protocols :refer [-downstream]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.p2p.hooks :refer [hook default-integrity-fn]]
            [replikativ.p2p.hash :refer [ensure-hash]]
            [kabel.middleware.block-detector :refer [block-detector]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string] :as read]
            [kioo.om :refer [content set-attr do-> substitute listen]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [om.dom :as omdom])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def err-ch (chan))

(go-loop [e (<! err-ch)]
  (when e
    (.error js/console "TOPIQ UNCAUGHT" e)
    (recur (<! err-ch))))

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))


(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params] (d/db-with old params))
              (fn [old params]
                (let [old (if-not (d/db? old)
                            (let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                                          :down-votes {:db/cardinality :db.cardinality/many}
                                          :posts {:db/cardinality :db.cardinality/many}
                                          :arguments {:db/cardinality :db.cardinality/many}
                                          :hashtags {:db/cardinality :db.cardinality/many}}
                                  conn   (d/create-conn schema)]
                              @conn)
                            old)]
                  (d/db-with old params)))})


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

(def val-atom (atom {}))

(defn topiqs-view
  "Builds topiqs list with topiq head and related argument list, resolves conflicts"
  [stage app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-topiq false
       :stage stage})
    om/IRenderState
    (render-state [this {:keys [selected-topiq stage] :as state}]
      (let [user (get-in @stage [:config :user])
            _ (println "NEW VAL" app)
            val (om/value app)]
        (cond (= (type val) replikativ.crdt.cdvcs.realize/Conflict) ;; TODO implement with protocol dispatch
              (do
                (sc/merge! stage [user #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                           (concat (map :id (:commits-a val))
                                   (map :id (:commits-b val))))
                (omdom/div nil (str "Resolving conflicts... please wait. " (pr-str val))))

              (= (type val) replikativ.crdt.cdvcs.stage/Abort) ;; reapply
              (do
                (sc/transact stage [user #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"] (:aborted val))
                (omdom/div nil (str "Retransacting your changes on new value... " (:aborted val))))

              (isa? (type val) js/Error)
              (omdom/div {:style "color:red"} (pr-str val))

              :else
              (if selected-topiq
                (topiq-arguments val owner val-atom)
                (topiqs val owner)))))))




(def trusted-hosts (atom #{:replikativ.stage/stage (.getDomain uri)}))


(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@topiq.es" "lisp"}))



;; for initial read
(read/register-tag-parser! 'replikativ.crdt.CDVCS map->CDVCS)

(go
  (def store
    (<! (new-mem-store
         (atom (read-string "{#uuid \"1e82f126-532d-5718-a77c-7920559fff74\" (fn replace [old params] params), #uuid \"2a5f89e0-c122-5b7f-83f1-a4fbd9c3821f\" {:transactions [[#uuid \"1e82f126-532d-5718-a77c-7920559fff74\" #uuid \"334e2480-c2dc-5c26-8208-37274e1e7aca\"]], :ts #inst \"2016-01-09T13:07:51.666-00:00\", :parents [#uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\"], :crdt :cdvcs, :version 1, :author \"eve@topiq.es\", :crdt-refs #{}}, #uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\" {:transactions [], :parents [], :crdt :cdvcs, :version 1, :ts #inst \"2016-01-09T13:07:24.034-00:00\", :author \"eve@topiq.es\", :crdt-refs #{}}, #uuid \"334e2480-c2dc-5c26-8208-37274e1e7aca\" #datascript/DB {:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :posts {:db/cardinality :db.cardinality/many}, :arguments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :datoms []}, [\"eve@topiq.es\" #uuid \"26558dfe-59bb-4de4-95c3-4028c56eb5b5\"] {:crdt :cdvcs, :description nil, :public false, :state #replikativ.crdt.CDVCS{:commit-graph {#uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\" [], #uuid \"2a5f89e0-c122-5b7f-83f1-a4fbd9c3821f\" [#uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\"]}, :heads #{#uuid \"2a5f89e0-c122-5b7f-83f1-a4fbd9c3821f\"}, :cursor nil, :store nil, :version 1}}, #uuid \"3b0197ff-84da-57ca-adb8-94d2428c6227\" (fn store-blob-trans [old params] (if *custom-store-fn* (*custom-store-fn* old params) old))}")))))

  (def hooks (atom {}))


  (def peer (client-peer "CLIENT-PEER"
                         store
                         err-ch
                         :middleware (comp (partial block-detector :client-core)
                                           (partial hook hooks store)
                                           (partial fetch store (atom {}) err-ch)
                                           ensure-hash
                                           (partial block-detector :client-surface))))

  (def stage (<! (s/create-stage! "eve@topiq.es" peer err-ch eval-fn)))


  (<! (s/subscribe-crdts! stage
                          {"eve@topiq.es"
                           #{#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}}))


  ;; fix back to functions in production
  (<! (s/connect!
       stage
       (str
        (if ssl?  "wss://" "ws://")
        (.getDomain uri)
        (when (= (.getDomain uri) "localhost")
          (str ":" 8080 #_(.getPort uri)))
        "/replikativ/ws")))


  (defn login-fn [new-user]
    (go
      (swap! stage assoc-in [:config :user] new-user)
      (<! (sc/fork! stage ["eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]))
      (swap! hooks assoc ["eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
             [[new-user #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
              default-integrity-fn true])))

  (om/root
   (partial navbar-view login-fn)
   nil
   {:target (. js/document (getElementById "collapsed-navbar-group"))})


  ;; stream changes into datascript
  (let [[p _] (get-in @stage [:volatile :chans])
        pub-ch (chan)]
    (async/sub p :pub/downstream pub-ch)
    (go-loop [{{{{{new-heads :heads cg :commit-graph} :op
                  method :method}
                 #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}
                (get-in @stage [:config :user])} :downstream :as pub} (<! pub-ch)

              applied #{}]
      ;; HACK for single commit ops to work with commit-history-values by setting commit as root
      (when-not (applied new-heads)
        (let [cg (if (= 1 (count cg)) (assoc cg (first new-heads) []) cg)
              cdvcs (or (get-in @stage [(get-in @stage [:config :user])
                                        #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state])
                        (key->crdt :cdvcs))
              heads (:heads (-downstream cdvcs pub))]
          (cond (= 1 (count heads))
                (let [txs (mapcat :transactions (<! (commit-history-values store cg (first new-heads))))]
                  (swap! val-atom
                         #(reduce (partial trans-apply eval-fn)
                                  %
                                  (filter (comp not empty?) txs))))
                :else
                (reset! val-atom (<! (summarize-conflict store eval-fn cdvcs))))))
      (recur (<! pub-ch) (conj applied new-heads))))


  (om/root
   (partial topiqs-view stage)
   val-atom
   {:target (. js/document (getElementById "topiq-container"))}))


(comment
  ;; jack in figwheel cljs REPL
  (require 'figwheel-sidecar.repl-api)
  (figwheel-sidecar.repl-api/cljs-repl)

  (get-in @(:state store) [["eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"] :state])
  (dissoc (get-in @stage ["eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state]) :store)

  (dissoc (get-in @stage ["foo@bar.com" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state]) :store)

  (get-in @stage ["banana@joe.com" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" :state :heads])

  (get-in @stage [:config])

  ;; recreate database
  (sc/create-cdvcs! stage
                    :id #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                    :description "topiq discourse.")
  (let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                :down-votes {:db/cardinality :db.cardinality/many}
                :posts {:db/cardinality :db.cardinality/many}
                :arguments {:db/cardinality :db.cardinality/many}
                :hashtags {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go
      (<! (sc/transact stage ["eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                       '(fn replace [old params] params)
                       @conn))
      (<! (sc/commit! stage {"eve@topiq.es" #{#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}}))))


  (go
    (let [start (js/Date.)]
      (doseq [i (range 1e3)]
        (<! (sc/transact stage ["eve@topiq.es" #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                         '(fn [old params] (d/db-with old params))
                         [{:db/unique-identity [:item/id (uuid)]
                           :title (str i)
                           :detail-text  (str i)
                           :author "benchmark@topiq.es"
                           :ts (js/Date.)}]))
        (<! (sc/commit! stage {"eve@topiq.es" #{#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}}))
        (<! (timeout 100)))
      (def benchmark (- (js/Date.) start))))

  (-> @stage :volatile :peer deref :volatile :store :state deref keys)

  )
