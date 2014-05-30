(ns link-collective.core
  (:require [weasel.repl :as ws-repl]
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

(ws-repl/connect "ws://localhost:17782" :verbose true)

(.log js/console "HAIL TO THE LAMBDA!")

(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params]
                 (:db-after (d/transact old params)))
              (fn [old params]
                (:db-after (d/transact old params)))})

(def val-ch (chan))

#_(let [schema {:up-votes {:db/cardinality :db.cardinality/many}
                :down-votes {:db/cardinality :db.cardinality/many}
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


#_(go
    (def store
      (<! (new-mem-store
           (atom (read-string "{#uuid \"029f3ec1-f6f9-5398-8507-3612cb99e109\" #datascript.DB{:schema {:up-votes {:db/cardinality :db.cardinality/many}, :down-votes {:db/cardinality :db.cardinality/many}, :comments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :ea {}, :av {}, :max-eid 0, :max-tx 536870912}, #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\" (fn replace [old params] params), #uuid \"2cab7867-38ef-5421-be45-461eb7619b61\" {:transactions [[#uuid \"029f3ec1-f6f9-5398-8507-3612cb99e109\" #uuid \"123ed64b-1e25-59fc-8c5b-038636ae6c3d\"]], :parents [], :ts #inst \"2014-05-30T15:20:53.536-00:00\", :author \"eve@polyc0l0r.net\"}, \"eve@polyc0l0r.net\" {#uuid \"fc9f725a-20eb-42f1-bb27-80d6fb2d1945\" {:branches {\"master\" #{#uuid \"2cab7867-38ef-5421-be45-461eb7619b61\"}}, :id #uuid \"fc9f725a-20eb-42f1-bb27-80d6fb2d1945\", :description \"link-collective discourse.\", :head \"master\", :last-update #inst \"2014-05-30T15:20:53.536-00:00\", :schema {:type \"http://github.com/ghubber/geschichte\", :version 1}, :causal-order {#uuid \"2cab7867-38ef-5421-be45-461eb7619b61\" []}, :public false, :pull-requests {}}}}")))))

    (def peer (client-peer "CLIENT-PEER" store))

    (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

    (async/tap (get-in @stage [:volatile :val-mult]) val-ch))




(comment
  (s/subscribe-repos! stage
                      {"eve@polyc0l0r.net" {#uuid "fc9f725a-20eb-42f1-bb27-80d6fb2d1945"
                                            #{"master"}}})

  (get-in @stage ["eve@polyc0l0r.net" #uuid "fc9f725a-20eb-42f1-bb27-80d6fb2d1945"])

  (get-in @stage [:volatile :val])
  (let [post-id (uuid)
        comment-id1 (uuid)
        comment-id2 (uuid)
        ts (js/Date.)]
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "fc9f725a-20eb-42f1-bb27-80d6fb2d1945"
                         "master"]
                        [{:db/id post-id
                          :title "Spiegel Online"
                          :content "http://spiegel.de #spon"
                          :author "bob@polyc0l0r.net"
                          :ts ts}
                         {:db/id post-id
                          :comments comment-id1}
                         {:db/id post-id
                          :comments comment-id2}
                         {:db/id comment-id1
                          :content "this is boring :-/"
                          :author "kordano@polyc0l0r.net"
                          :ts ts}
                         {:db/id comment-id2
                          :content "i like pizza"
                          :author "joe@polyc0l0r.net"
                          :ts ts}]
                        '(fn [old params]
                           (:db-after (d/transact old params)))))))

  (d/q '[:find ?e
         :where
         [?e :author "bob@polyc0l0r.net"]]
       (get-in @stage [:volatile :val
                       "eve@polyc0l0r.net" #uuid "fc9f725a-20eb-42f1-bb27-80d6fb2d1945" "master"]))

  (go (<! (s/commit! stage {"eve@polyc0l0r.net" {#uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"
                                                 #{"master"}}}))))
