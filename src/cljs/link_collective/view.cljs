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



;; --- navbar templates and functions ---

(defn handle-nav-text-change [e owner]
  (om/set-state! owner :input-text (.. e -target -value)))


(deftemplate right-navbar "templates/nav.html"
  [owner {:keys [set-user? current-user input-placeholder input-text]}]
  {[:#nav-current-user]
   (content
    (html
     [:a {:href "#"
          :class (if set-user? "" "navbar-link")
          :on-click #(do
                       (om/set-state! owner :set-user? (not set-user?))
                       (if (not set-user?)
                         (om/set-state! owner :input-text current-user)))}
      [:span.glyphicon.glyphicon-user]])
    " "
    current-user)

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

(defsnippet post-comment "main.html" [:.comment]
  [comment]
  {[:.comment-text] (content (:content comment))
   [:.comment-author] (content (:author comment))
   [:.comment-timestamp] (content (str (:ts comment)))})


(defsnippet post-header-hashtag "main.html" [:.post-header-hashtag]
  [hashtag]
  {[:.post-header-hashtag-text] (content hashtag)})


(defsnippet post-detail "main.html" [:.post-detail]
  [post app]
  {[:.post-detail] (set-attr "id" (str "post-detail-" (:id post)))
   [:.post-detail-url] (do-> (set-attr "href" (:detail-url post))
                             (content (:detail-url post)))
   [:.post-detail-text] (content (:detail-text post))
   [:.comments] (content (map post-comment (get-comments (:id post) app)))})


(defsnippet post-header "main.html" [:.post-header]
  [post owner]
  {[:.post-header]
   (do->
    (listen
     :on-click
     (fn [e]
       ;; will cleanup this mess and migrate some of it into view state
       (let [selected-entries (om/get-state owner :selected-entries)]
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


(defsnippet post "main.html" [:.post]
  [post app owner]
  {[:.post] (set-attr "id" (str "post-" (:id post)))
   [:.comment-counter] (content (-> (get-comments (:id post) app) count))
   [:.post-header] (substitute (post-header post owner))
   [:.post-detail] (substitute (post-detail post app))})


(defsnippet posts "main.html" [:#post-list]
  [app owner]
  {[:.list-group] (content (map #(post % app owner) (get-posts app)))})



;; --- general input templates and functions ---

(defn commit [{:keys [add-post add-comment selected-entries]}]
  (let [username (.-innerText (sel1 :#nav-current-user))]
    (if (= "" username)
      (.log js/console "Not logged in!")
      (if (empty? selected-entries)
        (add-post username)
        (add-comment username (last selected-entries))))))


(defsnippet general-input "main.html" [:.input-group]
  [state]
  {[:#general-input-form]
   (listen :on-key-press #(when (= (.-keyCode %) 10) (commit state)))
   [:#send-button]
   (listen :on-click (fn [e] (commit state)))})
