(ns link-collective.view
  (:require [figwheel.client :as fw :include-macros true]
            [kioo.om :refer [html-content content after set-attr do-> substitute listen prepend append html remove-class add-class]]
            [kioo.core :refer [handle-wrapper]]
            [datascript :as d]
            [dommy.utils :as utils]
            [dommy.core :as dommy]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [markdown.core :as md])
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
        result (map (partial zipmap [:id :post :content :author :ts])
                    (d/q '[:find ?p ?pid ?content ?author ?ts
                           :in $
                           :where
                           [?p :post ?pid]
                           [?p :content ?content]
                           [?p :author ?author]
                           [?p :ts ?ts]]
                         db))]
    (filter
     #(= (:post %) post-id)
     (sort-by :ts
              (map (fn [{:keys [id] :as p}]
                     (assoc p :hashtags (first (first (d/q '[:find (distinct ?hashtag)
                                                             :in $ ?pid
                                                             :where
                                                             [?h :comment ?pid]
                                                             [?h :tag ?hashtag]]
                                                           db
                                                           id)))))
                   result)))))



;; --- navbar templates and functions ---

(defn handle-text-change [e owner text]
  (om/set-state! owner text (.. e -target -value)))


(defn set-navbar-user
  "Set user name in user nav field and reset login text"
  [owner name]
  (do
    (om/set-state! owner :current-user name)
    (om/set-state! owner :login-user-text "")))


(deftemplate navbar "templates/tool.html"
  [owner {:keys [current-user search-text login-user-text search-placeholder]}]
  {[:#nav-input-field] (do-> (set-attr :placeholder search-placeholder)
                             (content search-text)
                             (listen :on-change #(handle-text-change % owner :search-text)))
   [:#nav-current-user] (do-> (content current-user)
                              (listen :on-change #(.log js/console (.. % -target -value))))
   [:#login-user-input] (do-> (set-attr :value login-user-text)
                              (listen :on-change #(handle-text-change % owner :login-user-text)))
   [:#login-user-password] (set-attr :disabled true)
   [:#modal-login-btn] (listen :on-click #(set-navbar-user owner login-user-text))
   [:#register-user-input] (set-attr :disabled true)
   [:#register-user-password] (set-attr :disabled true)
   [:#forgot-user-input] (set-attr :disabled true)})


;; --- user post templates ---

(defsnippet topiq-comment "templates/topiqs.html" [:.comment]
  [comment]
  {[:.comment-text] (html-content
                     (md/mdToHtml
                      (loop [text (:content comment)
                             hashtags (map name (:hashtags comment))]
                        (if (empty? hashtags)
                          text
                          (recur
                           (clojure.string/replace
                            text
                            (re-pattern (first hashtags))
                            (str"<a href='#'>" (first hashtags) "</a>"))
                           (rest hashtags))))))
   [:.comment-author] (content (:author comment))
   [:.comment-ts] (content
                   (let [time-diff (- (js/Date.) (:ts comment))]
                     (if (> time-diff 7200000)
                       (str (Math/round (/ time-diff 3600000)) " hours ago")
                       (if (< (Math/round (/ time-diff 60000)) 2)
                         "now"
                         (str (Math/round (/ time-diff 60000)) " minutes ago")))))})


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
   [:.topiq-text] (html-content
                   (loop [text (:title topiq)
                          hashtags (map name (:hashtags topiq))]
                        (if (empty? hashtags)
                          text
                          (recur
                           (clojure.string/replace
                            text
                            (re-pattern (first hashtags))
                            (str"<a href='#'>" (first hashtags) "</a>"))
                           (rest hashtags))))(md/mdToHtml (:title topiq)))
   [:.topiq-author] (content (:author topiq))
   [:.topiq-ts] (content
                 (let [time-diff (- (js/Date.) (:ts topiq))]
                   (if (> time-diff 3600000)
                     (str (Math/round (/ time-diff 3600000)) " hours ago")
                     (if (< (Math/round (/ time-diff 60000)) 2)
                       "now"
                       (str (Math/round (/ time-diff 60000)) " minutes ago")))))
   [:.topiq-comments] (set-attr :id (str "comments-" (:id topiq)))
   [:.comments] (content (map topiq-comment (get-comments (:id topiq) app)))})


(defn commit [owner]
  (let [username (.-innerHTML (sel1 :#nav-current-user))
        add-post (om/get-state owner :add-post)
        add-comment (om/get-state owner :add-comment)
        selected-entries (om/get-state owner :selected-entries)]
    (if (= "Not logged in" username)
      (js/alert "Please login or register.")
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
