(ns link-collective.view
  (:require [figwheel.client :as fw :include-macros true]
            [kioo.om :refer [content after set-attr do-> substitute listen prepend append html remove-class add-class]]
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

;; --- datascript queries ---

(defn get-posts [stage]
  (let [db (om/value
            (get-in stage ["eve@polyc0l0r.net"
                           #uuid "b09d8708-352b-4a71-a845-5f838af04116"
                           "master"]))]
    (sort-by
     :ts
     (map (partial zipmap [:id :title :detail-url :detail-text :author :ts])
          (d/q '[:find ?p ?title ?durl ?dtext ?author ?ts
                 :where
                 [?p :author ?author]
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
        result (sort-by
                :ts
                (map (partial zipmap [:id :post :content :author :ts])
                     (d/q '[:find ?p ?pid ?content ?author ?ts
                            :in $
                            :where
                            [?p :post ?pid]
                            [?p :content ?content]
                            [?p :author ?author]
                            [?p :ts ?ts]]
                          db)))]
    (filter #(= (:post %) post-id) result)))


;; --- navbar snippets and templates ---
(defn handle-text-change [e owner]
  (om/set-state! owner :input-text (.. e -target -value)))

(deftemplate right-navbar "templates/nav.html"
  [owner {:keys [set-user? current-user input-placeholder input-text]}]
  {[:#nav-current-user]
   (content
    (html
     [:a {:href "#"
          :class (if set-user? "" "navbar-link")
          :on-click #(om/set-state! owner :set-user? (not set-user?))}
      [:span.glyphicon.glyphicon-user]])
    " "
    current-user)

   [:#nav-input-field]
   (do->
    (set-attr :value input-text)
    (set-attr :placeholder input-placeholder)
    (listen
     :on-change #(handle-text-change % owner)
     :on-key-press
     #(when (== (.-keyCode %) 13)
        (when set-user?
          (om/set-state! owner :set-user? false)
          (om/set-state! owner :current-user input-text))
        (om/set-state! owner :input-text "")
        (om/set-state! owner :input-placeholder "Search ..."))))})


;; --- collection snippets and templates ---

(defsnippet link-detail-comment "main.html" [:.link-detail-comment-item]
  [comment]
  {[:.link-detail-comment-text] (content (:content comment))
   [:.link-detail-comment-user] (content (:author comment))
   [:.link-detail-comment-timestamp] (content (str (:ts comment)))})


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
  {[:.link-header]
   (do->
    (listen
     :on-click
     (fn [e]
       (let [selected-entries (om/get-state owner :selected-entries)]
         (if (some #{(:id record)} selected-entries)
           (do
             (dommy/remove-class! (sel1 (str "#link-item-" (:id record))) :selected-entry)
             (if (> (count selected-entries) 1)
               (dommy/add-class! (sel1 (str "#link-item-" (-> (remove #(= % (:id record)) selected-entries) last))) :selected-entry)
               (do
                 (dommy/set-attr! (sel1 :#general-input-form) :placeholder "Write a new post ...")
                 (dommy/remove-class! (sel1 :#send-button-icon) :glyphicon-comment)
                 (dommy/add-class! (sel1 :#send-button-icon) :glyphicon-send)))
             (om/set-state!
              owner
              :selected-entries
              (vec (remove #(= % (:id record)) selected-entries))))
           (do
             (doseq [link-header (sel :.link-item)]
               (dommy/remove-class! link-header :selected-entry))
             (dommy/add-class! (sel1 (str "#link-item-" (:id record))) :selected-entry)
             (if (empty? selected-entries)
               (do
                 (dommy/set-attr! (sel1 :#general-input-form) :placeholder "Write a comment...")
                 (dommy/remove-class! (sel1 :#send-button-icon) :glyphicon-send)
                 (dommy/add-class! (sel1 :#send-button-icon) :glyphicon-comment)))
             (om/set-state!
              owner
              :selected-entries
              (conj selected-entries (:id record))))))))
    (set-attr "href" (str "#link-detail-" (:id record))))
   [:.link-header-text] (content (:title record))
   [:.link-header-user] (content (:author record))
   [:.link-header-hashtag-list] (content (map link-header-hashtag (:hashtags record)))})


(defsnippet link-item "main.html" [:.link-item]
  [record app owner]
  {[:.link-item] (set-attr "id" (str "link-item-" (:id record)))
   [:.link-comment-counter] (content (-> (get-comments (:id record) app) count))
   [:.link-header] (substitute (link-header record owner))
   [:.link-detail] (substitute (link-detail record app))})


(defn commit [owner]
  (let [add-post (om/get-state owner :add-post)
        add-comment (om/get-state owner :add-comment)
        selected-entries (om/get-state owner :selected-entries)
        username (.-innerText (sel1 :#nav-current-user))]
    (if (= "" username)
      (.log js/console "Not logged in!")
      (if (empty? selected-entries)
        (add-post username)
        (add-comment username (last selected-entries))))))

(deftemplate main-view "main.html" [app owner]
  {[:.list-group] (content (map #(link-item % app owner) (get-posts app)))
   [:#send-button]
   (listen
    :on-click
    (fn [e] (commit owner)))
   [:#general-input-form]
   (listen
    :on-key-press
    #(when (= (.-keyCode %) 10)
       (commit owner)))})
