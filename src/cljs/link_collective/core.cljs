(ns link-collective.core
  (:require [link-collective.view :refer [posts right-navbar]]
            [clojure.data :refer [diff]]
            [domina :as dom]
            [figwheel.client :as figw :include-macros true]
            [weasel.repl :as ws-repl]
            [hasch.core :refer [uuid]]
            [datascript :as d]
            [geschichte.stage :as s]
            [geschichte.sync :refer [client-peer]]
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


;; todo
;; - load in templates

;; weasel websocket
(if (= "localhost" (.getDomain uri))
  (do
    (figw/watch-and-reload
     ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
     :jsload-callback (fn [] (print "reloaded")))
    (ws-repl/connect "ws://localhost:17782" :verbose true)))

(println "ALLE MACHT DEM KOLLEKTIV!")

(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params]
                 (:db-after (d/transact old params)))
              (fn [old params]
                (:db-after (d/transact old params)))})

#_(let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                :down-votes {:db/cardinality :db.cardinality/many}
                :posts {:db/cardinality :db.cardinality/many}
                :comments {:db/cardinality :db.cardinality/many}
                :hashtags {:db/cardinality :db.cardinality/many}}
        conn   (d/create-conn schema)]
    (go (<! (s/create-repo! stage
                            "eve@polyc0l0r.net"
                            "link-collective discourse."
                            @conn
                            "master"))))


(def url-regexp #"(https?|ftp)://[a-z0-9-]+(\.[a-z0-9-]+)+(/[\w-?#\.,]+)*(/[\w-\.,]+)*")


(defn add-post [stage author]
  (let [post-id (uuid)
        ts (js/Date.)
        text (dom/value (dom/by-id "general-input-form"))
        hash-tags (re-seq #"#[\w\d-_]+" text)
        urls (->> text
                  (re-seq url-regexp)
                  (map first))]
    (dom/set-value! (dom/by-id "general-input-form") "")
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"]
                        (concat [{:db/id post-id
                                  :title (str (apply str (take 160 text)) "...")
                                  :detail-url (first urls)
                                  :detail-text  text
                                  :author author
                                  :ts ts}]
                                (map (fn [t] {:db/id (uuid)
                                             :post post-id
                                             :tag (keyword t)
                                             :ts ts}) hash-tags))
                        '(fn [old params]
                           (:db-after (d/transact old params)))))
        (<! (s/commit! stage
                       {"eve@polyc0l0r.net"
                        {#uuid "b09d8708-352b-4a71-a845-5f838af04116" #{"master"}}})))))


(defn add-comment [stage author post-id]
  (let [comment-id (uuid)
        ts (js/Date.)
        text (dom/value (dom/by-id "general-input-form"))
        hash-tags (re-seq #"#[\w\d-_]+" text)
        urls (->> text
                  (re-seq url-regexp)
                  (map first))]
    (dom/set-value! (dom/by-id "general-input-form") "")
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"]
                        (concat
                         [{:db/id comment-id
                           :post post-id
                           :content text
                           :author author
                           :ts ts}])
                        '(fn [old params]
                           (:db-after (d/transact old params)))))
        (<! (s/commit! stage
                       {"eve@polyc0l0r.net"
                        {#uuid "b09d8708-352b-4a71-a845-5f838af04116" #{"master"}}})))))


;; we can do this runtime wide here, since we only use this datascript version
(read/register-tag-parser! 'datascript.DB datascript/map->DB)
(read/register-tag-parser! 'datascript.Datom datascript/map->Datom)


(go
  (def store
    (<! (new-mem-store
         ;; empty db
         (atom (read-string
                "{#uuid \"11a1c86b-935c-5a80-9c48-e0095321f738\" #datascript.DB{:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :posts {:db/cardinality :db.cardinality/many}, :comments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :ea {}, :av {}, :max-eid 0, :max-tx 536870912}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), #uuid \"2425a9dc-7ce8-56a6-9f52-f7c431afcd91\" {:transactions [[#uuid \"11a1c86b-935c-5a80-9c48-e0095321f738\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-05-30T21:20:05.808-00:00\", :author \"eve@polyc0l0r.net\"}, \"eve@polyc0l0r.net\" {#uuid \"b09d8708-352b-4a71-a845-5f838af04116\" {:branches {\"master\" #{#uuid \"2425a9dc-7ce8-56a6-9f52-f7c431afcd91\"}}, :id #uuid \"b09d8708-352b-4a71-a845-5f838af04116\", :description \"link-collective discourse.\", :head \"master\", :last-update #inst \"2014-05-30T21:20:05.808-00:00\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :causal-order {#uuid \"2425a9dc-7ce8-56a6-9f52-f7c431afcd91\" []}, :public false, :pull-requests {}}}}"))
         (atom  {'datascript.DB datascript/map->DB
                 'datascript.Datom datascript/map->Datom}))))

  (def peer (client-peer "CLIENT-PEER" store))

  (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

  (<! (s/subscribe-repos! stage
                          {"eve@polyc0l0r.net"
                           {#uuid "b09d8708-352b-4a71-a845-5f838af04116"
                            #{"master"}}}))

  (<! (timeout 500))

  ;; fix back to functions in production
  (<! (s/connect!
       stage
       (str
        (if ssl?  "wss://" "ws://")
        (.getDomain uri)
        ":"
        (if (= (.getDomain uri) "localhost")
          8084
          (.getPort uri))
        "/geschichte/ws")))


  (defn posts-view [app owner]
    (reify
      om/IInitState
      (init-state [_]
        {:selected-entries []
         :add-post (partial add-post stage)
         :add-comment (partial add-comment stage)})
      om/IRenderState
      (render-state [this {:keys [selected-entries add-comment add-post] :as state}]
        (if (= (type (om/value (get-in app ["eve@polyc0l0r.net"
                                            #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                                            "master"])))
               geschichte.stage/Conflict)
          (omdom/div nil (str "Conflict: " (om/value app)))
          (om/build posts app {:init-state state})))))


  (defn nav-view [app owner]
    (reify
      om/IInitState
      (init-state [_]
        {:set-user? true
         :current-user ""
         :input-placeholder "Search ..."
         :input-text ""})
      om/IRenderState
      (render-state [this {:keys [set-user? current-user input-text input-placeholder] :as state}]
        (right-navbar
         owner
         (if set-user?
           (assoc state :input-placeholder "Punch in email address ...")
           state)))))

  (om/root
   nav-view
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "lc-nav-container"))})

  (om/root
   posts-view
   (get-in @stage [:volatile :val-atom])
   {:target (. js/document (getElementById "main-container"))})


  )


(comment
  (def eve-data (get-in @stage [:volatile :val-atom]))

  (let [db (get-in @eve-data ["eve@polyc0l0r.net"
                                     #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                                     "master"])
             qr (sort-by :ts
                         (map (partial zipmap [:id :title :detail-url :detail-text :user :ts])
                              (d/q '[:find ?p ?title ?durl ?dtext ?user ?ts
                                     :where
                                     [?p :user ?user]
                                     [?p :detail-url ?durl]
                                     [?p :detail-text ?dtext]
                                     [?p :title ?title]
                                     [?p :ts ?ts]]
                                   db)))]
    qr)


  (let [db (get-in @eve-data ["eve@polyc0l0r.net"
                               #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                               "master"])]
    (map (partial zipmap [:id :post :content :user :ts])
         (d/q '[:find ?p ?pid ?content ?user ?ts
                :in $
                :where
                [?p :post ?pid]
                [?p :content ?content]
                [?p :user ?user]
                [?p :ts ?ts]]
              db)))



  [{:user "jane"
    :id 1
    :title "How to get a Designer"
    :detail-url "https://medium.com/coding-design/how-to-get-a-designer-b3afdf5a853d"
    :detail-text "Just some thoughts ..."
    :comments [{:text "awesome :D" :user "adam" :date "today"}]
    :hashtags #{"#coding" "#design"}}
   {:user "john"
    :id 2
    :title "Greenwald's 'No Place to Hide': a compelling, vital narrative about official criminality"
    :detail-text "Interesting article"
    :detail-url "http://boingboing.net/2014/05/28/greenwalds-no-place-to-hid.html"
    :comments [{:text "lies, all lies ..." :user "adam" :date "yesterday"}
               {:text "Sucker" :user "eve" :date "today"}]
    :hashtags #{"#greenwald" "#snowden" "#nsa"}}]


  (let [post-id (uuid)
        comment-id1 (uuid)
        ts (js/Date.)]
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"]
                        [{:db/id post-id
                          :title "Greenwald's 'No Place to Hide': a compelling, vital narrative about official criminality"
                          :detail-text "Interesting article"
                          :detail-url "http://boingboing.net/2014/05/28/greenwalds-no-place-to-hid.html"
                          :user "jane"
                          :ts ts}
                         {:db/id comment-id1
                          :post post-id
                          :content "awesome :D"
                          :user "adam"
                          :date "today"}]
                        '(fn [old params]
                           (:db-after (d/transact old params)))))))

)
