(ns topiq.db
  (:require [datascript.core :as d]
            [om.core :as om :include-macros true] ;; TODO avoid in db?
            [hasch.core :refer [uuid]]
            [replikativ.crdt.cdvcs.stage :as s]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))


(def url-regexp #"(https?|ftp)://[a-z0-9\u00a1-\uffff-]+(\.[a-z0-9\u00a1-\uffff-]+)+(:\d{2,5})?(/\S+)?")

(def hashtag-regexp #"(^|\s|\.|;|,|!|-)(#[\w\d\u00a1-\uffff_-]+)")

;; --- datascript queries ---

(defn vote-count [db topiq-id]
  (let [vote-query '[:find (count ?vote)
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

(defn- rank [{:keys [ts vote-count]}]
  (* vote-count (.exp js/Math (/ (- ts (.getTime (js/Date.)))
                                 ;; scale decay so we don't hit zero rank too quickly
                                 (* 7 24 3600 1000)))))


(defn get-topiqs [db]
  (let [qr (map (partial zipmap [:id :title #_:detail-url :detail-text :author :ts])
                (d/q '[:find ?p ?title #_?durl ?dtext ?author ?ts
                       :where
                       [?p :author ?author]
                       #_[?p :detail-url ?durl]
                       [?p :detail-text ?dtext]
                       [?p :title ?title]
                       [?p :ts ?ts]]
                     db))]
    (sort-by
     (fn [t] (- (rank t)))
     (map (fn [{:keys [id] :as p}]
            (.log js/console "RANK" (rank (assoc p :vote-count (vote-count db id))))
            (assoc p
                   :hashtags (first (first (d/q '[:find (distinct ?hashtag)
                                                  :in $ ?pid
                                                  :where
                                                  [?h :post ?pid]
                                                  [?h :tag ?hashtag]]
                                                db
                                                id)))
                   :vote-count (vote-count db id)))
          qr))))


(defn get-topiq [id db]
  (d/entity db id))


(defn get-arguments [post-id db]
  (let [query '{:find [?p ?author ?content ?ts]
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
                                          [?h :argument ?pid]
                                          [?h :tag ?hashtag]]
                                        db
                                        id))))
      result))))


(defn- extract-hashtags [text]
  (map #(nth % 2) (re-seq hashtag-regexp text)))

(defn add-topiq
  "Transacts a new topiq to the stage"
  [stage author text]
  (let [post-id (uuid)
        ts (js/Date.)
        hash-tags (extract-hashtags text)
        urls (->> text
                  (re-seq url-regexp)
                  (map first))]
    (go (<! (s/transact stage
                        [author #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                        '(fn [old params] (d/db-with old params))
                        (concat [(merge {:db/unique-identity [:item/id post-id]
                                         :title (str (apply str (take 160 text)) "...")
                                         :detail-text  text
                                         :author author
                                         :ts ts}
                                        (when-let [u (first urls)]
                                          {:detail-url u}))]
                                (map (fn [t] {:db/unique-identity [:item/id (uuid)]
                                             :post post-id
                                             :tag (keyword t)
                                             :ts ts}) hash-tags)
                                (map (fn [u] {:db/unique-identity [:item/id (uuid)]
                                             :post post-id
                                             :url u
                                             :ts ts}) urls))))
        (<! (s/commit! stage {author #{#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}})))))


(defn add-argument [stage author post-id text]
  (let [argument-id (uuid)
        ts (js/Date.)
        hash-tags (extract-hashtags text)
        urls (->> text
                  (re-seq url-regexp)
                  (map first))]
    (go (<! (s/transact stage
                        [author #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                        '(fn [old params] (d/db-with old params))
                        (concat
                         [{:db/unique-identity [:item/id argument-id]
                           :post post-id
                           :content text
                           :author author
                           :ts ts}]
                         (map (fn [t]
                                {:db/unique-identity [:item/id (uuid)]
                                 :argument argument-id
                                 :tag (keyword t)
                                 :ts ts})
                              hash-tags)
                         (map (fn [u] {:db/unique-identity [:item/id (uuid)]
                                      :post post-id
                                      :url u
                                      :ts ts}) urls))))
        (<! (s/commit! stage {author #{#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}})))))


(defn add-vote [stage topiq-id voter updown]
  (let [ts (js/Date.)]
    (when-not (= "Not logged in" voter)
      (go (<! (s/transact stage
                          [voter
                           #uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"]
                          '(fn [old params] (d/db-with old params))
                          [{:db/unique-identity [:item/id (uuid [voter topiq-id])]
                            :topiq topiq-id
                            :voter voter
                            :updown updown
                            :ts ts}]))
          (<! (s/commit! stage {voter #{#uuid "26558dfe-59bb-4de4-95c3-4028c56eb5b5"}}))))))
