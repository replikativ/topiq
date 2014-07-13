(ns link-collective.view
  (:require [link-collective.db :refer [get-topiq get-topiqs get-comments vote-count
                                        add-post add-comment add-vote]]
            [link-collective.plugins :refer [render-content replace-hashtags]]
            [figwheel.client :as fw :include-macros true]
            [kioo.om :refer [html-content content after set-attr do-> substitute listen prepend append html remove-class add-class]]
            [kioo.core :refer [handle-wrapper]]
            [datascript :as d]
            [dommy.utils :as utils]
            [dommy.core :as dommy]
            [om.core :as om :include-macros true]
            [domina :as dom])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]
                   [dommy.macros :refer [node sel sel1]]))

(enable-console-print!)


(println "Resistance is futile!")


(defn compute-time-diff
  [timestamp]
  (let [time-diff (- (.getTime (js/Date.)) timestamp)]
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


(defn commit [owner]
  (let [username (.-innerHTML (sel1 :#nav-current-user))
        stage (om/get-state owner :stage)
        selected-topiq (om/get-state owner :selected-topiq)]
    (if (= "Not logged in" username)
      (js/alert "Please login or register.")
      (try
        (if selected-topiq
          (add-comment stage username selected-topiq (dom/value (dom/by-id "general-input-form")))
          (add-post stage username (dom/value (dom/by-id "general-input-form"))))
        (dom/set-value! (dom/by-id "general-input-form") "")
        (catch js/Object e
            (js/alert e))))))


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
  {[:.comment-text] (-> (:content comment)
                        render-content
                        html-content)
   [:.comment-author] (content (:author comment))
   [:.comment-ts] (content (compute-time-diff (:ts comment)))})

(defsnippet topiq-vote-group "templates/topiqs.html" [:.topiq-vote-group]
  [stage app topiq voter]
  {[:.glyphicon-chevron-up] (listen :on-click #(add-vote stage (:id topiq) voter :up))
   [:.vote-counter] (content (:vote-count topiq))
   [:.glyphicon-chevron-down] (listen :on-click #(add-vote stage (:id topiq) voter :down))})



(defsnippet topiq "templates/topiqs.html" [:.topiq]
  [topiq app owner]
  {[:.topiq] (set-attr "id" (str "topiq-" (:id topiq)))
   [:.comment-counter] (content (-> (get-comments (:id topiq) app) count))
   [:.comment-ref] (do->
                    (set-attr :href (str "#" (:id topiq)))
                    (listen :on-click #(om/set-state! owner :selected-topiq (:id topiq))))
   [:.topiq-text] (html-content
                   (let [text (replace-hashtags (:title topiq))]
                     (if (:detail-url topiq)
                       (clojure.string/replace
                        text
                        (re-pattern (:detail-url topiq))
                        (str "<a href='" (:detail-url topiq) "' target='_blank'>"(:detail-url topiq) "</a>"))
                       text)))
   [:.topiq-author] (content (:author topiq))
   [:.topiq-ts] (content (compute-time-diff (:ts topiq)))
   [:.topiq-vote-group] (substitute (topiq-vote-group (om/get-state owner :stage)
                                                      app
                                                      topiq
                                                      (.-innerHTML (sel1 :#nav-current-user))))})

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
