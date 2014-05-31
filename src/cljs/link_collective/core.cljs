(ns link-collective.core
  (:require [link-collective.view :refer [main-view]]
            [clojure.data :refer [diff]]
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
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(enable-console-print!)

;; fire up repl
#_(do
    (ns weasel.startup)
    (require 'weasel.repl.websocket)
    (cemerick.piggieback/cljs-repl
        :repl-env (weasel.repl.websocket/repl-env
                   :ip "0.0.0.0" :port 17782)))

;; todo
;; - load in templates

(figw/defonce conn (ws-repl/connect "ws://localhost:17782" :verbose true))

(.log js/console "HAIL TO THE LAMBDA!")

(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params]
                 (:db-after (d/transact old params)))
              (fn [old params]
                (:db-after (d/transact old params)))})

(def val-ch (chan))

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

(read/register-tag-parser! 'datascript.DB datascript/map->DB)
(read/register-tag-parser! 'datascript.Datom datascript/map->Datom)


(go
  (def store
    (<! (new-mem-store
         ;; empty db
         (atom (read-string
                "{#uuid \"11a1c86b-935c-5a80-9c48-e0095321f738\" #datascript.DB{:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :posts {:db/cardinality :db.cardinality/many}, :comments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :ea {}, :av {}, :max-eid 0, :max-tx 536870912}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), #uuid \"2425a9dc-7ce8-56a6-9f52-f7c431afcd91\" {:transactions [[#uuid \"11a1c86b-935c-5a80-9c48-e0095321f738\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-05-30T21:20:05.808-00:00\", :author \"eve@polyc0l0r.net\"}, \"eve@polyc0l0r.net\" {#uuid \"b09d8708-352b-4a71-a845-5f838af04116\" {:branches {\"master\" #{#uuid \"2425a9dc-7ce8-56a6-9f52-f7c431afcd91\"}}, :id #uuid \"b09d8708-352b-4a71-a845-5f838af04116\", :description \"link-collective discourse.\", :head \"master\", :last-update #inst \"2014-05-30T21:20:05.808-00:00\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :causal-order {#uuid \"2425a9dc-7ce8-56a6-9f52-f7c431afcd91\" []}, :public false, :pull-requests {}}}}")))))

  (def peer (client-peer "CLIENT-PEER" store))

  (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

  (async/tap (get-in @stage [:volatile :val-mult]) val-ch)

  (s/subscribe-repos! stage
                      {"eve@polyc0l0r.net"
                       {#uuid "b09d8708-352b-4a71-a845-5f838af04116"
                        #{"master"}}})

  (om/root
   (fn [stage-value owner]
     (om/component
      (main-view
       (let [db (get-in @stage
                        [:volatile :val
                         "eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"])
             qr (map (partial zipmap [:id :title :detail-url :detail-text :user])
                     (d/q '[:find ?p ?title ?dtext ?durl ?user
                            :where
                            [?p :user ?user]
                            [?p :detail-url ?durl]
                            [?p :detail-text ?dtext]
                            [?p :title ?title]]
                          db))]
         qr))))
   stage
   {:target (. js/document (getElementById "main-container"))})

  (<! (timeout 500))

  (let [post-id (uuid)
        ts (js/Date.)]
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"]
                        [{:db/id post-id
                          :title "How to get a Designer"
                          :detail-url "https://medium.com/coding-design/how-to-get-a-designer-b3afdf5a853d"
                          :detail-text "Just some thoughts ..."
                          :user "jane"
                          :ts ts}
                         {:db/id (uuid)
                          :post post-id
                          :content "awesome :D"
                          :user "adam"
                          :date "today"}
                         {:db/id (uuid)
                          :post post-id
                          :tag :#coding}
                         {:db/id (uuid)
                          :post post-id
                          :tag :#design}
                         {:db/id (uuid)
                          :post post-id
                          :user "adam"
                          :text "awesome :D"
                          :date "today"}]
                        '(fn [old params]
                           (:db-after (d/transact old params))))))))




(comment


  (get-in @stage ["eve@polyc0l0r.net" #uuid "b09d8708-352b-4a71-a845-5f838af04116"])

  (get-in @stage [:volatile :val])


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
                         #uuid "fc9f725a-20eb-42f1-bb27-80d6fb2d1945"
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

  (map (partial zipmap [:id :title :detail-url :detail-text :user])
       (d/q '[:find ?p ?title ?dtext ?durl ?user
              :in $
              :where
              [?p :user ?user]
              [?p :detail-url ?durl]
              [?p :detail-text ?dtext]
              [?p :title ?title]]
            (get-in @stage [:volatile :val
                            "eve@polyc0l0r.net"
                            #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                            "master"])))

  (d/q '[:find ?p (max 10 ?t)
         :in $ ?amount
         :where [?h :post ?p]
         [?h :tag ?t]]
       (get-in @stage [:volatile :val
                       "eve@polyc0l0r.net"
                       #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                       "master"]))


  (go (<! (s/commit! stage {"eve@polyc0l0r.net" {#uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"
                                                 #{"master"}}})))


  (go-loop [{{{new-db "master"} #uuid "b09d8708-352b-4a71-a845-5f838af04116"} "eve@polyc0l0r.net"}
            (<! val-ch)]
    (when new-db
      (om/transact app)

      (recur (<! val-ch))))

  )
