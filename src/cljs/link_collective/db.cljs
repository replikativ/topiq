(ns link-collective.db
  (:require [datascript :as d]
            [om.core :as om :include-macros true] ;; TODO avoid in db?
            [hasch.core :refer [uuid]]
            [geschichte.stage :as s]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(def url-regexp #"(https?|ftp)://[a-z0-9\u00a1-\uffff-]+(\.[a-z0-9\u00a1-\uffff-]+)+(:\d{2,5})?(/\S+)?")

(def hashtag-regexp #"(^|\s|\.|;|,|!|-)(#[\w\d\u00a1-\uffff_-]+)")



;; --- datascript queries ---

(defn get-topiqs [stage]
  (let [db (om/value
            (get-in stage ["eve@polyc0l0r.net"
                           #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                           "master"]))
        qr (map (partial zipmap [:id :title :detail-url :detail-text :author :ts])
                (d/q '[:find ?p ?title ?durl ?dtext ?author ?ts
                       :where
                       [?p :author ?author]
                       [?p :detail-url ?durl]
                       [?p :detail-text ?dtext]
                       [?p :title ?title]
                       [?p :ts ?ts]]
                     db))]
    (sort-by
     :ts
     (map (fn [{:keys [id] :as p}]
            (assoc p :hashtags (first (first (d/q '[:find (distinct ?hashtag)
                                                    :in $ ?pid
                                                    :where
                                                    [?h :post ?pid]
                                                    [?h :tag ?hashtag]]
                                                  db
                                                  id)))))
          qr))))


(defn get-topiq [id stage]
  (let [db (om/value
            (get-in stage ["eve@polyc0l0r.net"
                           #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                           "master"]))]
    (d/entity db id)))


(defn get-comments [post-id stage]
  (let [db (om/value
            (get-in stage ["eve@polyc0l0r.net"
                           #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                           "master"]))
        query '{:find [?p ?author ?content ?ts]
                :in [$ ?pid]
                :where [[?p :post ?pid]
                        [?p :content ?content]
                        [?p :author ?author]
                        [?p :ts ?ts]]}
        result (map (partial zipmap [:id :author :content :ts])
                    (d/q query db post-id))]
    (sort-by
     :ts
     (map
      (fn [{:keys [id] :as p}]
        (assoc p :hashtags (ffirst (d/q '[:find (distinct ?hashtag)
                                          :in $ ?pid
                                          :where
                                          [?h :comment ?pid]
                                          [?h :tag ?hashtag]]
                                        db
                                        id))))
      result))))


(defn vote-count [stage topiq-id]
  (let [db (om/value (get-in stage ["eve@polyc0l0r.net"
                                    #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                                    "master"]))
        vote-query '[:find (count ?vote)
                     :in $, ?topiq-id, ?updown
                     :where
                     [?vote :topiq ?topiq-id]
                     [?vote :voter ?voter]
                     [?vote :updown ?updown]]
        up-cnt (or (first (first (d/q vote-query db topiq-id :up)))
                   0)
        down-cnt (or (first (first (d/q vote-query db topiq-id :down)))
                     0)]
    (- up-cnt down-cnt)))

(defn- extract-hashtags [text]
  (map #(nth % 2) (re-seq hashtag-regexp text)))

(defn add-post
  "Transacts a new topiq to the stage"
  [stage author text]
  (let [post-id (uuid)
        ts (js/Date.)
        hash-tags (extract-hashtags text)
        urls (->> text
                  (re-seq url-regexp)
                  (map first))]
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
                                             :ts ts}) hash-tags)
                                (map (fn [u] {:db/id (uuid)
                                             :post post-id
                                             :url u
                                             :ts ts}) urls))
                        '(fn [old params]
                           (:db-after (d/transact old params)))))
        (<! (s/commit! stage
                       {"eve@polyc0l0r.net"
                        {#uuid "b09d8708-352b-4a71-a845-5f838af04116" #{"master"}}})))))


(defn add-comment [stage author post-id text]
  (let [comment-id (uuid)
        ts (js/Date.)
        hash-tags (extract-hashtags text)
        urls (->> text
                  (re-seq url-regexp)
                  (map first))]
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"]
                        (concat
                         [{:db/id comment-id
                           :post post-id
                           :content text
                           :author author
                           :ts ts}]
                         (map (fn [t]
                                {:db/id (uuid)
                                 :comment comment-id
                                 :tag (keyword t)
                                 :ts ts})
                              hash-tags)
                         (map (fn [u] {:db/id (uuid)
                                      :post post-id
                                      :url u
                                      :ts ts}) urls))
                        '(fn [old params]
                           (:db-after (d/transact old params)))))
        (<! (s/commit! stage
                       {"eve@polyc0l0r.net"
                        {#uuid "b09d8708-352b-4a71-a845-5f838af04116" #{"master"}}})))))


(defn add-vote [stage topiq-id voter updown]
  (let [ts (js/Date.)]
    (go (<! (s/transact stage
                        ["eve@polyc0l0r.net"
                         #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                         "master"]
                        [{:db/id (uuid)
                          :topiq topiq-id
                          :voter voter
                          :updown updown
                          :ts ts}]
                        '(fn [old params]
                           (:db-after (d/transact old params)))))
        (<! (s/commit! stage
                       {"eve@polyc0l0r.net"
                        {#uuid "b09d8708-352b-4a71-a845-5f838af04116" #{"master"}}})))))
