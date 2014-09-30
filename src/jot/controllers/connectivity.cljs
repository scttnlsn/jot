(ns jot.controllers.connectivity
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :as async :refer [>! chan put! timeout]]
            [jot.util :as util]))

(defn online? []
  (.-onLine js/navigator))

(defn channel []
  (let [ch (chan)
        cb #(put! ch {:online (online?)})]
    (.addEventListener js/window "online" cb)
    (.addEventListener js/window "offline" #(do (.stop js/window) (cb)))
    (util/debounce ch 10000)))

(defn connectivity! [{:keys [online]} state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (if online
      (put! sync-ch [:restore {}])))
  (assoc state :online online))
