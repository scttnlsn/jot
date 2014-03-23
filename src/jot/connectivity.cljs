(ns jot.connectivity
  (:require [cljs.core.async :as async :refer [chan put!]]))

(defn online? []
  (.-onLine js/navigator))

(def state (atom {:online (online?)}))

(defn- connectivity-changed []
  (reset! state {:online (online?)}))

(.addEventListener js/window "online" connectivity-changed)
(.addEventListener js/window "offline" connectivity-changed)