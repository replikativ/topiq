(ns link-collective.view
  (:require [figwheel.client :as fw :include-macros true]
            [kioo.om :refer [content set-attr do-> substitute listen prepend append html remove-class]]
            [kioo.core :refer [handle-wrapper]]
            [datascript :as d]
            [dommy.utils :as utils]
            [dommy.core :as dommy]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [dommy.macros :refer [node sel sel1]]))

(enable-console-print!)

(println "Resistance is futile!")

(fw/watch-and-reload
  ;; :websocket-url "ws://localhost:3449/figwheel-ws" default
 :jsload-callback (fn [] (print "reloaded"))) ;; optional callback


;; --- datascript queries ---

(defn get-posts [stage]
  (let [db (om/value
            (get-in stage ["eve@polyc0l0r.net"
                           #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                           "master"]))]
    (sort-by
     :ts
     (map (partial zipmap [:id :title :detail-url :detail-text :user :ts])
          (d/q '[:find ?p ?title ?durl ?dtext ?user ?ts
                 :where
                 [?p :user ?user]
                 [?p :detail-url ?durl]
                 [?p :detail-text ?dtext]
                 [?p :title ?title]
                 [?p :ts ?ts]]
               db)))))

(defn get-comments [post-id stage]
  (let [db (om/value
            (get-in stage ["eve@polyc0l0r.net"
                           #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                           "master"]))
        result (map (partial zipmap [:id :post :content :user :ts])
             (d/q '[:find ?p ?pid ?content ?user ?ts
                    :in $
                    :where
                    [?p :post ?pid]
                    [?p :content ?content]
                    [?p :user ?user]
                    [?p :ts ?ts]]
                  db))]
    (filter #(= (:post %) post-id) result)))


(defsnippet link-detail-comment "main.html" [:.link-detail-comment-item]
  [comment]
  {[:.link-detail-comment-text] (content (:content comment))
   [:.link-detail-comment-user] (content (:user comment))
   [:.link-detail-comment-timestamp] (content (:ts comment))})


(defsnippet link-header-hashtag "main.html" [:.link-header-hashtag-item]
  [hashtag]
  {[:.link-header-hashtag-item-text] (content hashtag)})


(defsnippet link-detail "main.html" [:.link-detail]
  [record app]
  {[:.link-detail] (set-attr "id" (str "link-detail-" (:id record)))
   [:.link-detail-url] (do-> (set-attr "href" (:detail-url record))
                             (content (:detail-url record)))
   [:.link-detail-text] (content (:detail-text record))
   [:.link-detail-comment-list] (content (map link-detail-comment (get-comments (:id record) app)))})


(defsnippet link-header "main.html" [:.link-header]
  [record owner]
  {[:.link-header] (do->
                    (listen :on-click
                            (fn [e]
                              (let [selected-entries (om/get-state owner :selected-entries)]
                                (if (some #{(:id record)} selected-entries)
                                  (do
                                    (dommy/remove-class! (sel1 (str "#link-item-" (:id record))) :selected-entry)
                                    (if (> (count selected-entries) 1)
                                      (dommy/add-class! (sel1 (str "#link-item-" (-> selected-entries butlast last))) :selected-entry))
                                    (om/set-state!
                                     owner
                                     :selected-entries
                                     (vec (remove #(= % (:id record)) selected-entries))))
                                  (do
                                    (doseq [link-header (sel :.link-header)]
                                      (dommy/remove-class! link-header :selected-entry))
                                    (dommy/add-class! (sel1 (str "#link-item-" (:id record))) :selected-entry)
                                    (om/set-state!
                                     owner
                                     :selected-entries
                                     (conj selected-entries (:id record))))))))
                    (set-attr "id" (str "link-item-" (:id record)))
                    (set-attr "href" (str "#link-detail-" (:id record))))
   [:.link-header-text] (content (:title record))
   [:.link-header-user] (content (:user record))
   [:.link-header-hashtag-list] (content (map link-header-hashtag (:hashtags record)))})


(defsnippet link-item "main.html" [:.link-item]
  [record app owner]
  {[:.link-comment-counter] (content (-> record :comments count))
   [:.link-header] (substitute (link-header record owner))
   [:.link-detail] (substitute (link-detail record app))})


(deftemplate main-view "main.html" [app owner]
  {[:.list-group] (content (map #(link-item % app owner) (get-posts app)))
   [:#send-button] (listen
                    :on-click
                    (fn [e]
                      (let [add-post (om/get-state owner :add-post)
                            add-comment (om/get-state owner :add-comment)
                            selected-entries (om/get-state owner :selected-entries)]
                        (if (empty? selected-entries)
                          (add-post e)
                          (add-comment (last selected-entries))))))})
