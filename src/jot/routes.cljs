(ns jot.routes
  (:require [secretary.core :as secretary :include-macros true :refer [defroute]])
  (:import goog.History
           goog.history.EventType))

(secretary/set-config! :prefix "#")

(def listen (.-listen goog.events))
(def navigate-event (.-NAVIGATE goog.events.EventType))

(defn define-routes! [app-state]
  (defroute "/" {}
    (swap! app-state assoc :route {:name :note-list}))

  (defroute "/notes/:id" {id :id}
    (let [path (str "/" id)
          notes (:notes @app-state)
          note (get notes path)]
      (swap! app-state assoc :route {:name :note-edit
                                     :note note})))
  
  (defroute "/settings" {}
    (swap! app-state assoc :route {:name :settings}))
  
  (let [h (History.)]
    (goog.events/listen h EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
    (doto h (.setEnabled true))))