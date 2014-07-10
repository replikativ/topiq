(ns link-collective.plugins
  (:require [link-collective.db :refer [hashtag-regexp]]
            [markdown.core :as md]
            [clojure.string :as str]))


;; static pipline for now, will be factored out for js and runtime config later


(def pre-markdown-plugins identity)


(defn special-chars [s]
  (clojure.string/replace s #"\'" "&rsquo;"))

(defn replace-hashtags
  "Replace hashtags in string with html references"
  [s]
  (str/replace s hashtag-regexp "<a href='#h=$2'>$2</a>" ))

(defn img-responsive [s]
  (str/replace s "<img " "<img class=\"img-responsive\""))

(defn post-markdown-plugins [s]
  (-> s
      replace-hashtags
      img-responsive))

(defn render-content [s]
  (-> s
      pre-markdown-plugins
      md/mdToHtml
      post-markdown-plugins))
