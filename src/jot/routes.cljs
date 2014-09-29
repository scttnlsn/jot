(ns jot.routes
  (:require [cljs.core.async :refer [put!]]
            [secretary.core :as secretary :include-macros true :refer [defroute]])
  (:import goog.History
           goog.history.EventType))

(secretary/set-config! :prefix "#")

(defn default-route []
  [:notes {}])

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
