(ns jot.controllers.connectivity
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [>! chan put! timeout]]))

(defn online? []
  (.-onLine js/navigator))

(defn listen [conn-ch]
  (let [cb (fn []
             (put! conn-ch {:online (online?)}))]
    (.addEventListener js/window "online" cb)
    (.addEventListener js/window "offline" cb)
    (cb)))

(defn connectivity! [{:keys [online]} state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (if online
      (go
       (<! (timeout 1000))
       (put! sync-ch [:push-all-dirty {}]))))
  (assoc state :online online))
