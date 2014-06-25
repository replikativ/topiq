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

(defsnippet post-comment "templates/posts.html" [:.comment]
  [comment]
  {[:.comment-text] (content (:content comment))
   [:.comment-author] (content (:author comment))
   [:.comment-ts] (content (str (:ts comment)))})


(defsnippet post-header-hashtag "templates/posts.html" [:.post-header-hashtag]
  [hashtag]
  {[:.post-header-hashtag-text] (content (name hashtag))})


(defsnippet post-detail "templates/posts.html" [:.post-detail]
  [post app]
  {[:.post-detail] (set-attr "id" (str "post-detail-" (:id post)))
   [:.post-detail-url] (do-> (set-attr "href" (:detail-url post))
                             (content (:detail-url post)))
   [:.post-detail-text] (content (:detail-text post))
   [:.comments] (content (map post-comment (get-comments (:id post) app)))})


(defsnippet post-header "templates/posts.html" [:.post-header]
  [post owner]
  {[:.post-header]
   (do->
    (listen
     :on-click
     (fn [e]
       ;; will cleanup this mess and migrate some of it into view state
       (let [selected-entries (om/get-state owner :selected-entries)]
         (.log js/console (str selected-entries))
         (if (some #{(:id post)} selected-entries)
           (do
             (dommy/remove-class! (sel1 (str "#post-" (:id post))) :selected-entry)
             (if (> (count selected-entries) 1)
               (dommy/add-class! (sel1 (str "#post-" (-> (remove #(= % (:id post)) selected-entries) last))) :selected-entry)
               (do
                 (dommy/set-attr! (sel1 :#general-input-form) :placeholder "Write a new post ...")
                 (dommy/remove-class! (sel1 :#send-button-icon) :glyphicon-comment)
                 (dommy/add-class! (sel1 :#send-button-icon) :glyphicon-send)))
             (om/set-state!
              owner
              :selected-entries
              (vec (remove #(= % (:id post)) selected-entries))))
           (do
             (doseq [post-header (sel :.post)]
               (dommy/remove-class! post-header :selected-entry))
             (dommy/add-class! (sel1 (str "#post-" (:id post))) :selected-entry)
             (if (empty? selected-entries)
               (do
                 (dommy/set-attr! (sel1 :#general-input-form) :placeholder "Write a comment...")
                 (dommy/remove-class! (sel1 :#send-button-icon) :glyphicon-send)
                 (dommy/add-class! (sel1 :#send-button-icon) :glyphicon-comment)))
             (om/set-state! owner :selected-entries (conj selected-entries (:id post))))))))
    (set-attr "href" (str "#post-detail-" (:id post))))
   [:.post-header-text] (content (:title post))
   [:.post-header-author] (content (:author post))
   [:.post-header-hashtags] (content (map post-header-hashtag (:hashtags post)))})


(defsnippet post "templates/posts.html" [:.post]
  [post app owner]
  {[:.post] (set-attr "id" (str "post-" (:id post)))
   [:.comment-counter] (content (-> (get-comments (:id post) app) count))
   [:.post-header] (substitute (post-header post owner))
   [:.post-detail] (substitute (post-detail post app))})


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


(deftemplate posts "templates/posts.html"
  [app owner]
  {[:.list-group] (content (map #(post % app owner) (get-posts app)))
   [:#general-input-form] (listen :on-key-down #(if (= (.-keyCode %) 10)
                                                  (commit owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (commit owner)))))
   [:#send-button] (listen :on-click (fn [e] (commit owner)))})
