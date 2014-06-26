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



;; --- navbar templates and functions ---

(defn handle-nav-text-change [e owner]
  (om/set-state! owner :input-text (.. e -target -value)))



(deftemplate right-navbar "templates/nav.html"
  [owner {:keys [set-user? current-user input-placeholder input-text]}]
  {[:#nav-user-btn]
   (do->
    (if set-user?
      (do->
       (add-class "text-danger")
       (remove-class "navbar-link"))
      (do->
       (remove-class "text-danger")
       (add-class "navbar-link")))
    (listen
     :on-click
     #(do
        (om/set-state! owner :set-user? (not set-user?))
        (if (not set-user?)
          (om/set-state! owner :input-text current-user)
          (om/set-state! owner :input-text "")))))
   [:#nav-current-user] (content current-user)
   [:#nav-input-field]
   (do->
    (set-attr :value input-text)
    (set-attr :placeholder input-placeholder)
    (listen
     :on-change #(handle-nav-text-change % owner)
     :on-key-press
     #(when (== (.-keyCode %) 13)
        (when set-user?
          (om/set-state! owner :set-user? false)
          (om/set-state! owner :current-user input-text))
        (om/set-state! owner :input-text "")
        (om/set-state! owner :input-placeholder "Search ..."))))})


;; --- user post templates ---

(defsnippet topiq-comment "templates/topiqs.html" [:.comment]
  [comment]
  {[:.comment-text] (content (:content comment))
   [:.comment-author] (content (:author comment))
   [:.comment-ts] (content (str (:ts comment)))})


(defsnippet topiq "templates/topiqs.html" [:.topiq]
  [topiq app owner]
  {[:.topiq] (set-attr "id" (str "topiq-" (:id topiq)))
   [:.comment-counter] (content (-> (get-comments (:id topiq) app) count))
   [:.topiq-header]
   (do->
    (listen
     :on-click
     (fn [e]
       ;; will cleanup this mess and migrate some of it into view state
       (let [selected-entries (om/get-state owner :selected-entries)]
         (.log js/console (str selected-entries))
         (if (some #{(:id topiq)} selected-entries)
           (do
             (dommy/remove-class! (sel1 (str "#topiq-" (:id topiq))) :selected-entry)
             (if (> (count selected-entries) 1)
               (dommy/add-class! (sel1 (str "#topiq-" (-> (remove #(= % (:id topiq)) selected-entries) last))) :selected-entry)
               (do
                 (dommy/set-attr! (sel1 :#general-input-form) :placeholder "Write a new topiq ...")
                 (dommy/remove-class! (sel1 :#send-button-icon) :glyphicon-comment)
                 (dommy/add-class! (sel1 :#send-button-icon) :glyphicon-send)))
             (om/set-state!
              owner
              :selected-entries
              (vec (remove #(= % (:id topiq)) selected-entries))))
           (do
             (doseq [topiq-header (sel :.topiq)]
               (dommy/remove-class! topiq-header :selected-entry))
             (dommy/add-class! (sel1 (str "#topiq-" (:id topiq))) :selected-entry)
             (if (empty? selected-entries)
               (do
                 (dommy/set-attr! (sel1 :#general-input-form) :placeholder "Write a comment...")
                 (dommy/remove-class! (sel1 :#send-button-icon) :glyphicon-send)
                 (dommy/add-class! (sel1 :#send-button-icon) :glyphicon-comment)))
             (om/set-state! owner :selected-entries (conj selected-entries (:id topiq))))))))
    (set-attr "href" (str "#comments-" (:id topiq))))
   [:.topiq-text] (content (:title topiq))
   [:.topiq-author] (content (:author topiq))
   [:.topiq-ts] (content (str (:ts topiq)))
   [:.topiq-comments] (set-attr :id (str "comments-" (:id topiq)))
   [:.comments] (content (map topiq-comment (get-comments (:id topiq) app)))})


(defn commit [owner]
  (let [username (.-innerHTML (sel1 :#nav-current-user))
        add-post (om/get-state owner :add-post)
        add-comment (om/get-state owner :add-comment)
        selected-entries (om/get-state owner :selected-entries)]
    (if (= "" username)
      (js/alert "Please type in username in the nav box")
      (try
        (if (empty? selected-entries)
          (add-post username)
          (add-comment username (last selected-entries)))
        (catch js/Object e
            (js/alert e))))))


(deftemplate topiqs "templates/topiqs.html"
  [app owner]
  {[:.list-group] (content (map #(topiq % app owner) (get-topiqs app)))
   [:#general-input-form] (listen :on-key-down #(if (= (.-keyCode %) 10)
                                                  (commit owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (commit owner)))))
   [:#send-button] (listen :on-click (fn [e] (commit owner)))})
