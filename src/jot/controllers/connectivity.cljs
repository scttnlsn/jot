(ns jot.controllers.connectivity
  (:require [cljs.core.async :as async :refer [chan put!]]))

(defn online? []
  (.-onLine js/navigator))

(defn listen [conn-ch]
  (let [cb (fn []
             (put! conn-ch (online?)))]
    (.addEventListener js/window "online" cb)
    (.addEventListener js/window "offline" cb)
    (cb)))

(defn connectivity! [params state]
  (assoc state :online (:online params)))
