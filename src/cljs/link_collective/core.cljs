(ns link-collective.core
  (:require [weasel.repl :as ws-repl]
            [hasch.core :refer [uuid]]
            [datascript :as d]
            [geschichte.stage :as s]
            [geschichte.sync :refer [client-peer]]
            [konserve.store :refer [new-mem-store]]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async]
            [cljs.reader :refer [read-string]]
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
;; - implement data model in repo
;;   - fix realize value
;; - load in templates

(ws-repl/connect "ws://localhost:17782" :verbose true)

(.log js/console "HAIL TO THE LAMBDA!")

(def eval-fn {'(fn replace [old params] params) (fn replace [old params] params)
              '(fn [old params]
                 (merge-with merge old params))
              (fn [old params]
                (merge-with merge old params))})

(def val-ch (chan))


#_(let [post {:title "Spiegel Online"
            :content "http://spiegel.de #spon"
            :author "kordano"
            :ts (js/Date.)}
      post-id (uuid post)
      comment {:content "this is boring :-/"
               :author "bob@polyc0l0r.net"
               :ts (js/Date.)}
      comment-id (uuid comment)
      vote {:author "bob@polyc0l0r.net" :type :down}
      vote-id (uuid vote)]
  (go (<! (s/create-repo! stage "eve@polyc0l0r.net" "link-collective discourse."
                          {:posts {post-id post}
                           :hashtags {(uuid :#spon) :#spon}
                           :hashtags->posts {(uuid :#spon) #{post-id}}
                           :comments {comment-id comment}
                           :posts->comments {post-id #{comment-id}}
                           :votes {vote-id vote}
                           :posts->votes {post-id #{vote-id}}}
                          "master"))))


(let [schema {:votes {:db/cardinality :db.cardinality/many}
              :comments {:db/cardinality :db.cardinality/many}
              :hashtags {:db/cardinality :db.cardinality/many}}
      conn   (d/create-conn schema)
      init-db (:db-after (d/transact @conn [{:db/id -1
                                             :title "Spiegel Online"
                                             :content "http://spiegel.de #spon"
                                             :author "bob@polyc0l0r.net"
                                             :ts (js/Date.)
                                             :hashtags [:#spon]
                                             :comments [{:content "this is boring :-/"
                                                         :author "kordano@polyc0l0r.net"
                                                         :ts (js/Date.)}]
                                             :votes [{:author "kordano@polyc0l0r.net"
                                                      :type :down}]}])) ]
  (pr-str init-db)
  #_(go (<! (s/create-repo! stage
                            "eve@polyc0l0r.net"
                            "link-collective discourse."
                            init-db
                            "master")))
  #_(d/q '[:find ?c ?e
         :where
         [?e :author "bob@polyc0l0r.net"]
         [?e :content ?c]]
       init-db))


(cljs.reader/register-tag-parser! "datascript.Datom" d/map->Datom)
(cljs.reader/register-tag-parser! "datascript.DB" d/map->DB)


(implements? IRecord (read-string "#datascript.DB{:schema {:votes {:db/cardinality :db.cardinality/many}, :comments {:db/cardinality :db.cardinality/many}, :hashtags {:db/cardinality :db.cardinality/many}}, :ea {1 {:comments #{#datascript.Datom{:e 1, :a :comments, :v {:content \"this is boring :-/\", :author \"kordano@polyc0l0r.net\", :ts #inst \"2014-05-26T17:29:27.972-00:00\"}, :tx 536870913, :added true}}, :title #{#datascript.Datom{:e 1, :a :title, :v \"Spiegel Online\", :tx 536870913, :added true}}, :content #{#datascript.Datom{:e 1, :a :content, :v \"http://spiegel.de #spon\", :tx 536870913, :added true}}, :ts #{#datascript.Datom{:e 1, :a :ts, :v #inst \"2014-05-26T17:29:27.972-00:00\", :tx 536870913, :added true}}, :hashtags #{#datascript.Datom{:e 1, :a :hashtags, :v :#spon, :tx 536870913, :added true}}, :votes #{#datascript.Datom{:e 1, :a :votes, :v {:author \"kordano@polyc0l0r.net\", :type :down}, :tx 536870913, :added true}}, :author #{#datascript.Datom{:e 1, :a :author, :v \"bob@polyc0l0r.net\", :tx 536870913, :added true}}}}, :av {:title {\"Spiegel Online\" #{#datascript.Datom{:e 1, :a :title, :v \"Spiegel Online\", :tx 536870913, :added true}}}, :content {\"http://spiegel.de #spon\" #{#datascript.Datom{:e 1, :a :content, :v \"http://spiegel.de #spon\", :tx 536870913, :added true}}}, :author {\"bob@polyc0l0r.net\" #{#datascript.Datom{:e 1, :a :author, :v \"bob@polyc0l0r.net\", :tx 536870913, :added true}}}, :ts {#inst \"2014-05-26T17:29:27.972-00:00\" #{#datascript.Datom{:e 1, :a :ts, :v #inst \"2014-05-26T17:29:27.972-00:00\", :tx 536870913, :added true}}}, :hashtags {:#spon #{#datascript.Datom{:e 1, :a :hashtags, :v :#spon, :tx 536870913, :added true}}}, :comments {{:content \"this is boring :-/\", :author \"kordano@polyc0l0r.net\", :ts #inst \"2014-05-26T17:29:27.972-00:00\"} #{#datascript.Datom{:e 1, :a :comments, :v {:content \"this is boring :-/\", :author \"kordano@polyc0l0r.net\", :ts #inst \"2014-05-26T17:29:27.972-00:00\"}, :tx 536870913, :added true}}}, :votes {{:author \"kordano@polyc0l0r.net\", :type :down} #{#datascript.Datom{:e 1, :a :votes, :v {:author \"kordano@polyc0l0r.net\", :type :down}, :tx 536870913, :added true}}}}, :max-eid 1, :max-tx 536870913}"))

#_(go (def store
        (<! (new-mem-store
             #_(atom {#uuid "0912a672-6bc2-5297-9ffa-948998517273"
                    {:transactions
                     [[#uuid "2b21fbe0-e8a8-563d-bfba-e4b6022d056f"
                       #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]],
                     :parents [],
                     :ts #inst "2014-05-24T19:14:16.158-00:00",
                     :author "eve@polyc0l0r.net"},
                    #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"
                    '(fn replace [old params] params),
                    "eve@polyc0l0r.net"
                    {#uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"
                     {:description "link-collective discourse.",
                      :schema {:type "http://github.com/ghubber/geschichte", :version 1},
                      :pull-requests {},
                      :causal-order {#uuid "0912a672-6bc2-5297-9ffa-948998517273" []},
                      :public false,
                      :branches
                      {"master" #{#uuid "0912a672-6bc2-5297-9ffa-948998517273"}},
                      :head "master",
                      :last-update #inst "2014-05-24T19:14:16.158-00:00",
                      :id #uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"}},
                    #uuid "2b21fbe0-e8a8-563d-bfba-e4b6022d056f"
                    {:posts
                     {#uuid "164564fb-f93b-5863-9ee2-4517a42d0e99"
                      {:title "Spiegel Online",
                       :content "http://spiegel.de #spon",
                       :author "kordano",
                       :ts #inst "2014-05-24T19:14:16.092-00:00"}},
                     :comments
                     {#uuid "098fc269-d8c1-524d-a049-9a8cc52d6268"
                      {:content "this is boring :-/",
                       :author "bob@polyc0l0r.net",
                       :ts #inst "2014-05-24T19:14:16.120-00:00"}},
                     :posts->comments
                     {#uuid "164564fb-f93b-5863-9ee2-4517a42d0e99"
                      #{#uuid "098fc269-d8c1-524d-a049-9a8cc52d6268"}},
                     :hashtags->posts
                     {#uuid "17d34c1b-0535-5a5b-8c2a-2113cae5aca3"
                      #{#uuid "164564fb-f93b-5863-9ee2-4517a42d0e99"}},
                     :posts->votes
                     {#uuid "164564fb-f93b-5863-9ee2-4517a42d0e99"
                      #{#uuid "254995fc-f837-530a-a4fa-a2923dc6db4a"}},
                     :hashtags {#uuid "17d34c1b-0535-5a5b-8c2a-2113cae5aca3" :#spon},
                     :votes
                     {#uuid "254995fc-f837-530a-a4fa-a2923dc6db4a"
                      {:author "bob@polyc0l0r.net", :type :down}}}}))))


      (def peer (client-peer "CLIENT-PEER" store))

      (def stage (<! (s/create-stage! "eve@polyc0l0r.net" peer eval-fn)))

      #_(async/tap (get-in @stage [:volatile :val-mult]) val-ch))




(comment
  (s/subscribe-repos! stage
                      {"eve@polyc0l0r.net" {#uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"
                                            #{"master"}}})

  (get-in @stage ["eve@polyc0l0r.net" #uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"])
  (get-in @stage [:volatile :val
                  "eve@polyc0l0r.net" #uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363" "master"])
  (get-in @stage [:volatile :val])

  (let [post {:title "Netzwertig"
              :content "http://netzwertig.de"
              :ts (js/Date.)}
        post-id (uuid post)]
    (go (<! (s/transact stage ["eve@polyc0l0r.net"
                               #uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"
                               "master"]
                        {:posts {post-id post}}
                        '(fn [old params]
                           (merge-with merge old params))))))

  (go (<! (s/commit! stage {"eve@polyc0l0r.net" {#uuid "1bc987e2-f19e-4f6a-9341-8858ad4d4363"
                                                 #{"master"}}}))))
