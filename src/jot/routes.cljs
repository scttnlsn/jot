(ns jot.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:require [cljs.core.async :refer [put!]]
            [secretary.core :as secretary])
  (:import goog.History
           goog.history.EventType))

(secretary/set-config! :prefix "#")

(defn default-route []
  [:notes {}])

(defn redirect [history path]
  (.replaceToken history (subs path 1)))

(defn define-routes! [nav-ch]
  (defroute notes-path "/" {}
    (put! nav-ch [:notes {}]))

  (defroute note-path "/notes/:id" {id :id}
    (put! nav-ch [:note {:id id}]))

  (defroute settings-path "/settings" {}
    (put! nav-ch [:settings {}])))

(defn create-history []
  (History.))

(defn start-history! [history]
  (goog.events/listen history EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
  (.setEnabled history true))
