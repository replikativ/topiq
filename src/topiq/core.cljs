(ns topiq.core
  (:require [topiq.view :refer [topiqs navbar topiq-arguments]]
            [clojure.data :refer [diff]]
            [hasch.core :refer [uuid]]
            [datascript.core :as d]
            [replikativ.stage :as s]
            [replikativ.crdt.cdvcs.realize :refer [branch-value commit-history-values trans-apply summarize-conflict]]
            [replikativ.crdt.cdvcs.stage :as sc]
            [replikativ.core :refer [client-peer]]
            [konserve.memory :refer [new-mem-store]]
            [konserve.core :as k]
            [replikativ.p2p.auth :refer [auth]]
            [replikativ.p2p.fetch :refer [fetch]]
            [replikativ.p2p.hooks :refer [hook default-integrity-fn]]
            [replikativ.p2p.hash :refer [ensure-hash]]
            [replikativ.p2p.block-detector :refer [block-detector]]
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
    (println "TOPIQ UNCAUGHT" e)
    (recur (<! err-ch))))

(def uri (goog.Uri. js/location.href))

(def ssl? (= (.getScheme uri) "https"))


(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params] (d/db-with old params)) (fn [old params] (d/db-with old params))})


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
                (sc/merge! stage [user
                                  #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                                  "master"]
                           (concat (map :id (:commits-a val))
                                   (map :id (:commits-b val))))
                (omdom/div nil (str "Resolving conflicts... please wait. " (pr-str val))))

              (= (type val) replikativ.crdt.cdvcs.stage/Abort) ;; reapply
              (do
                (sc/transact stage [user
                                    #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                                    "master"] (:aborted val))
                (omdom/div nil (str "Retransacting your changes on new value... " (:aborted val))))
              :else
              (if selected-topiq
                (topiq-arguments val owner val-atom)
                (topiqs val owner)))))))




(def trusted-hosts (atom #{:replikativ.stage/stage (.getDomain uri)}))


(defn- auth-fn [users]
  (go (js/alert (pr-str "AUTH-REQUIRED: " users))
    {"eve@topiq.es" "lisp"}))




(go
  (def store
    (<! (new-mem-store
         (atom (read-string
                "{#uuid \"1e82f126-532d-5718-a77c-7920559fff74\" (fn replace [old params] params), #uuid \"2a5f89e0-c122-5b7f-83f1-a4fbd9c3821f\" {:transactions [[#uuid \"1e82f126-532d-5718-a77c-7920559fff74\" #uuid \"334e2480-c2dc-5c26-8208-37274e1e7aca\"]], :ts #inst \"2015-12-18T23:23:26.481-00:00\", :branch \"master\", :parents [#uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\"], :crdt :repo, :version 1, :author \"eve@topiq.es\", :crdt-refs #{}}, #uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\" {:transactions [], :parents [], :crdt :repo, :version 1, :branch \"master\", :ts #inst \"2015-12-18T23:23:12.482-00:00\", :author \"eve@topiq.es\", :crdt-refs #{}}, #uuid \"334e2480-c2dc-5c26-8208-37274e1e7aca\" #datascript/DB {:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :posts {:db/cardinality :db.cardinality/many}, :arguments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :datoms []}, [\"eve@topiq.es\" #uuid \"26558dfe-59bb-4de4-95c3-4028c56eb5b5\"] {:crdt :repo, :description nil, :public false, :state {:commit-graph {#uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\" [], #uuid \"2a5f89e0-c122-5b7f-83f1-a4fbd9c3821f\" [#uuid \"3004b2bd-3dd9-5524-a09c-2da166ffad6a\"]}, :branches {\"master\" #{#uuid \"2a5f89e0-c122-5b7f-83f1-a4fbd9c3821f\"}}, :version 1}}, #uuid \"3b0197ff-84da-57ca-adb8-94d2428c6227\" (fn store-blob-trans [old params] (if *custom-store-fn* (*custom-store-fn* old params) old))}")))))

  (def hooks (atom {}))


  (def peer (client-peer "CLIENT-PEER"
                         store
                         err-ch
                         (comp  (partial block-detector :client-core)
                                (partial hook hooks store)
                                (partial fetch store err-ch)
                                ensure-hash
                                (partial block-detector :client-surface))))

  (def stage (<! (s/create-stage! "eve@topiq.es" peer err-ch eval-fn)))


  (<! (s/subscribe-crdts! stage
                          {"eve@topiq.es"
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
        "/replikativ/ws")))

  (println "connected.")

  (defn login-fn [new-user]
    (go
      (swap! stage assoc-in [:config :user] new-user)
      (<! (sc/fork! stage ["eve@topiq.es"
                           #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                           "master"]))
      (swap! hooks assoc ["eve@topiq.es"
                          #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                          "master"]
             [[new-user #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" "master"]
              default-integrity-fn true])))

  (om/root
   (partial navbar-view login-fn)
   nil
   {:target (. js/document (getElementById "collapsed-navbar-group"))})


  ;; stream changes into datascript
  (let [[p _] (get-in @stage [:volatile :chans])
        pub-ch (chan)]
    (async/sub p :pub/downstream pub-ch)
    (go-loop [{{{{{{heads "master"} :branches cg :commit-graph :as cdvcs} :op
                  method :method}
                 #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}
                (get-in @stage [:config :user])} :downstream :as pub} (<! pub-ch)]
      ;; HACK for single commit ops to work with commit-history-values by setting commit as root
      (let [cg (if (= 1 (count cg)) (assoc cg (first heads) []) cg)]
        #_(println "PUB" pub)
        (cond (= 1 (count heads))
              (let [txs (mapcat :transactions (<! (commit-history-values store cg (first heads))))]
                (swap! val-atom
                       #(reduce (partial trans-apply eval-fn)
                                %
                                (filter (comp not empty?) txs))))
              :else
              (summarize-conflict store eval-fn cdvcs "master"))
        (recur (<! pub-ch)))))


  (om/root
   (partial topiqs-view stage)
   val-atom
   {:target (. js/document (getElementById "topiq-container"))}))


(comment
  ;; jack in figwheel cljs REPL
  (require 'figwheel-sidecar.repl-api)
  (figwheel-sidecar.repl-api/cljs-repl)



  ;; recreate database
  (sc/create-repo! stage
                   :id #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                   :description "topiq discourse.")
  (let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                :down-votes {:db/cardinality :db.cardinality/many}
                :posts {:db/cardinality :db.cardinality/many}
                :arguments {:db/cardinality :db.cardinality/many}
                :hashtags {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go
      (<! (sc/transact stage ["eve@topiq.es"
                              #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                              "master"]
                       '(fn replace [old params] params)
                       @conn))
      (<! (sc/commit! stage {"eve@topiq.es" {#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" #{"master"}}})
          )))


  (go
    (doseq [i (range 100)]
      (<! (sc/transact stage ["eve@topiq.es"
                              #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"
                              "master"]
                       '(fn replace [old params] params)
                       i))
      (<! (sc/commit! stage {"eve@topiq.es" {#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5" #{"master"}}})
          )))

  (-> @stage :volatile :peer deref :volatile :store :state deref)

  )
