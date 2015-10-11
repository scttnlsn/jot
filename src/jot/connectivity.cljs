(ns jot.connectivity
  (:require [cljs.core.async :as async]
            [jot.util :as util]))

(defn online? []
  (.-onLine js/navigator))

(defn channel []
  (let [ch (async/chan)
        cb #(async/put! ch {:online? (online?)})]
    (.addEventListener js/window "online" cb)
    (.addEventListener js/window "offline" cb)
    (cb)
    ch))
