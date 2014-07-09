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


(defn replace-hashtags
  "Replace hashtags in text with html references"
  [raw-text raw-hashtags]
  (loop [text (clojure.string/replace raw-text #"\'" "&rsquo;")
         hashtags (map name raw-hashtags)]
    (if (empty? hashtags)
      text
      (recur
       (clojure.string/replace
        text
        (re-pattern (first hashtags))
        (str"<a href='#'>" (first hashtags) "</a>"))
       (rest hashtags)))))


(defn compute-time-diff
  [timestamp]
  (let [time-diff (- (js/Date.) timestamp)]
    (cond
     (>= time-diff 172800000) (str (Math/round (/ time-diff 86400000)) " days ago")
     (< 86399999 time-diff 172800000) "yesterday"
     (< 7199999 time-diff 86400000) (str (Math/round (/ time-diff 3600000)) " hours ago")
     (< 119999 time-diff 7200000 ) (str (Math/round (/ time-diff 60000)) " minutes ago")
     (< time-diff 120000) "now"
     :else "NaN")))


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

(defsnippet topiq-comment "templates/comment.html" [:.comment]
  [comment]
  {[:.comment-text] (-> (replace-hashtags (:content comment) (:hashtags comment))
                        md/mdToHtml
                        html-content)
   [:.comment-author] (content (:author comment))
   [:.comment-ts] (content (compute-time-diff (:ts comment)))})



(defsnippet topiq "templates/topiqs.html" [:.topiq]
  [topiq app owner]
  {[:.topiq] (set-attr "id" (str "topiq-" (:id topiq)))
   [:.comment-counter] (content (-> (get-comments (:id topiq) app) count))
   [:.comment-ref] (do->
                    (set-attr :href (str "#" (:id topiq)))
                    (listen :on-click #(om/set-state! owner :selected-topiq (:id topiq))))
   [:.topiq-text] (html-content
                   (let [text (replace-hashtags (:title topiq) (:hashtags topiq))]
                     (if (:detail-url topiq)
                       (clojure.string/replace
                        text
                        (re-pattern (:detail-url topiq))
                        (str "<a href='" (:detail-url topiq) "' target='_blank'>"(:detail-url topiq) "</a>"))
                       text)))
   [:.topiq-author] (content (:author topiq))
   [:.topiq-ts] (content (compute-time-diff (:ts topiq)))})


(defn commit [owner]
  (let [username (.-innerHTML (sel1 :#nav-current-user))
        add-post (om/get-state owner :add-post)
        add-comment (om/get-state owner :add-comment)
        selected-topiq (om/get-state owner :selected-topiq)]
    (if (= "Not logged in" username)
      (js/alert "Please login or register.")
      (try
        (if selected-topiq
          (add-comment username selected-topiq)
          (add-post username))
        (catch js/Object e
            (js/alert e))))))


(deftemplate comments "templates/comment.html"
  [app owner]
  {[:.topiq-text] (content (:title (get-topiq (om/get-state owner :selected-topiq) app)))
   [:.topiq-author] (content (:author (get-topiq (om/get-state owner :selected-topiq) app)))
   [:.topiq-ts] (content (compute-time-diff (:ts (get-topiq (om/get-state owner :selected-topiq) app))))
   [:#back-btn] (listen :on-click #(om/set-state! owner :selected-topiq nil))
   [:.comments] (content (map topiq-comment (get-comments (om/get-state owner :selected-topiq) app)))
   [:#general-input-form] (listen :on-key-down #(if (= (.-keyCode %) 10)
                                                  (commit owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (commit owner)))))
   [:#comment-btn] (listen :on-click (fn [e] (commit owner)))})


(deftemplate topiqs "templates/topiqs.html"
  [app owner]
  {[:.list-group] (content (map #(topiq % app owner) (get-topiqs app)))
   [:#general-input-form] (listen :on-key-down #(if (= (.-keyCode %) 10)
                                                  (commit owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (commit owner)))))
   [:#post-btn] (listen :on-click (fn [e] (commit owner)))})
