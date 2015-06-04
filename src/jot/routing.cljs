(ns jot.routing
  (:require [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-frame.core :refer [dispatch]]
            [secretary.core :as secretary :refer-macros [defroute]])
  (:import goog.History))

(secretary/set-config! :prefix "#")

(defn- strip-slash [s]
  (if (= "/" (last s))
    (clojure.string/replace s #"/$" "")
    s))

(defn- on-navigate [e]
  (-> e
      (.-token)
      (strip-slash)
      (secretary/dispatch!)))

(def history (History.))

(defn start-history! []
  (events/listen history EventType/NAVIGATE on-navigate)
  (.setEnabled history true))

(defn visit! [path]
  (aset js/window.location "hash" path))

(defroute note-index-path "/" {}
  (dispatch [:navigate [:note-index {}]]))

(defroute note-edit-path "/notes/:id" {id :id}
  (dispatch [:navigate [:note-edit {:id id}]]))

(defroute settings-path "/settings" {}
  (dispatch [:navigate [:settings {}]]))

(defn default-route []
  [:note-index {}])
