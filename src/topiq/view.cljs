(ns topiq.view
  (:require [topiq.db :refer [get-topiq get-topiqs get-arguments vote-count
                                        add-topiq add-argument add-vote url-regexp]]
            [topiq.plugins :refer [render-content replace-hashtags]]
            [kioo.om :refer [html-content content after set-attr do-> substitute
                             listen prepend append html remove-class add-class]]
            [kioo.core :refer [handle-wrapper]]
            [om.core :as om :include-macros true]
            [domina :as dom])
  (:require-macros [kioo.om :refer [defsnippet deftemplate]]))

(enable-console-print!)


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
  (let [username (.-innerHTML (dom/by-id "nav-current-user"))
        stage (om/get-state owner :stage)
        selected-topiq (om/get-state owner :selected-topiq)]
    (if (= "Not logged in" username)
      (js/alert "Please login or register.")
      (try
        (let [text (dom/value (dom/by-id "general-input-form"))]
          (if (> (count text) 10)
            (if selected-topiq
              (add-argument stage username selected-topiq text)
              (add-topiq stage username text))
            (js/alert "Not enough input.")))
        (dom/set-value! (dom/by-id "general-input-form") "")
        (catch js/Object e
            (js/alert e))))))


(deftemplate navbar "templates/tool.html"
  [owner {:keys [current-user search-text login-user-text search-placeholder login-fn]}]
  {#_[:#nav-input-field] #_(do-> (set-attr :placeholder search-placeholder)
                                 (content search-text)
                                 (listen :on-change #(handle-text-change % owner :search-text)))
   [:#nav-current-user] (do-> (content current-user)
                              (listen :on-change #(.log js/console (.. % -target -value))))
   [:#login-user-input] (do-> (set-attr :value login-user-text)
                              (listen :on-change #(handle-text-change % owner :login-user-text)))
   [:#login-user-password] (set-attr :disabled true)
   [:#modal-login-btn] (listen :on-click (fn [e] (set-navbar-user owner login-user-text)
                                           (login-fn login-user-text)))
   [:#register-user-input] (set-attr :disabled true)
   [:#register-user-password] (set-attr :disabled true)
   [:#forgot-user-input] (set-attr :disabled true)})


(defn expose-links [text]
  (let [free-url #"([\(]?)((https?|ftp)://[a-z0-9\u00a1-\uffff-]+(\.[a-z0-9\u00a1-\uffff-]+)+(:\d{2,5})?(/\S+)?)[\)]?"
        links (re-seq free-url text)]
    (reduce (fn [text [_ par url]]
              (if (empty? par)
                (.replace text url (str "[" url "](" url ")"))
                text)) text links)))

;; --- user post templates ---
(defsnippet topiq-argument "templates/argument.html" [:.argument]
  [argument]
  {[:.argument-text] (-> (:content argument)
                         expose-links
                         render-content
                         html-content)
   [:.argument-author] (content (:author argument))
   [:.argument-ts] (content (compute-time-diff (:ts argument)))})

(defsnippet topiq-vote-group "templates/topiqs.html" [:.topiq-vote-group]
  [stage app topiq voter]
  {[:.glyphicon-chevron-up] (listen :on-click #(add-vote stage (:id topiq) voter :up))
   [:.vote-counter] (content (:vote-count topiq))
   [:.glyphicon-chevron-down] (listen :on-click #(add-vote stage (:id topiq) voter :down))})



(defsnippet topiq "templates/topiqs.html" [:.topiq]
  [topiq app owner]
  {[:.topiq] (set-attr "id" (str "topiq-" (:id topiq)))
   [:.argument-counter] (content (-> (get-arguments (:id topiq) app) count))
   [:.argument-ref] (do->
                     (set-attr :href (str "#" (:id topiq)))
                     (listen :on-click #(om/set-state! owner :selected-topiq (:id topiq))))
   ;; XSS attack possible?
   [:.topiq-text] (html-content
                   (let [text (replace-hashtags (:title topiq))]
                     (reduce
                      #(clojure.string/replace %1 %2 (str "<a href='" %2 "' target='_blank'> link </a>"))
                      text
                      (map first (re-seq url-regexp text)))))
   [:.topiq-author] (content (:author topiq))
   [:.topiq-ts] (content (compute-time-diff (:ts topiq)))
   [:.topiq-vote-group] (substitute (topiq-vote-group (om/get-state owner :stage)
                                                      app
                                                      topiq
                                                      (.-innerHTML (dom/by-id "nav-current-user"))))})

;; WARNING: pure "arguments" name clashes with compiler
(deftemplate topiq-arguments "templates/argument.html"
  [app owner]
  {[:.topiq-text] (html-content
                   (let [topiq (get-topiq (om/get-state owner :selected-topiq) app)
                         text (replace-hashtags (:title topiq))]
                     (reduce
                      #(clojure.string/replace %1 %2 (str "<a href='" %2 "' target='_blank'> link </a>"))
                      text
                      (map first (re-seq url-regexp text)))))
   [:.topiq-author] (content (:author (get-topiq (om/get-state owner :selected-topiq) app)))
   [:.topiq-ts] (content (compute-time-diff (:ts (get-topiq (om/get-state owner :selected-topiq) app))))
   [:#back-btn] (listen :on-click #(om/set-state! owner :selected-topiq nil))
   [:.arguments] (content (map topiq-argument (get-arguments (om/get-state owner :selected-topiq) app)))
   [:#general-input-form] (listen :on-key-down #(if (= (.-keyCode %) 10)
                                                  (commit owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (commit owner)))))
   [:#argument-btn] (listen :on-click (fn [e] (commit owner)))})


(deftemplate topiqs "templates/topiqs.html"
  [app owner]
  {[:#general-input-form] (listen :on-key-down #(if (= (.-keyCode %) 10)
                                                  (commit owner)
                                                  (when (= (.-which %) 13)
                                                    (when (.-ctrlKey %)
                                                      (commit owner)))))
   [:#post-btn] (listen :on-click (fn [e] (commit owner)))
   [:.list-group] (content (for [t (get-topiqs app)] (topiq t app owner)))})
