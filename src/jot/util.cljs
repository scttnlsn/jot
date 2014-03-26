(ns jot.util
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop]]
    [jot.macros :refer [<?]])
  (:require
    [cljs.core.async :as async :refer [alts! >! chan close! put! timeout]]))

(defprotocol IError
  (-error? [this]))

(extend-type default
  IError
  (-error? [this] false))

(extend-protocol IError
  js/Error
  (-error? [this] true))

(defn throw-error [x]
  (if (-error? x)
    (throw x)
    x))

(defn async-result-chan [f & args]
  (let [ch (chan)
        cb (fn [err & results]
             (go
               (if err
                 (>! ch err)
                 (>! ch results))
               (close! ch)))
        result (apply f (concat args [cb]))]
    [ch result]))

(defn async-chan [f & args]
  (let [[ch _] (apply async-result-chan (cons f args))]
    ch))

(defn navigate [url]
  (set! (.. js/window -location) url))

(defn navigate-back []
  (.back (.. js/window -history)))

(defn debounce [in ms]
  (let [out (chan)]
    (go-loop [last-val nil]
      (let [val (if (nil? last-val) (<! in) last-val)
            timer (timeout ms)
            [new-val ch] (alts! [in timer])]
        (condp = ch
          timer (do (>! out val) (recur nil))
          in (recur new-val))))
    out))

(defn uuid []
  (let [r (repeatedly 30 (fn [] (.toString (rand-int 16) 16)))]
    (apply str (concat (take 8 r) ["-"]
                       (take 4 (drop 8 r)) ["-4"]
                       (take 3 (drop 12 r)) ["-"]
                       [(.toString  (bit-or 0x8 (bit-and 0x3 (rand-int 15))) 16)]
                       (take 3 (drop 15 r)) ["-"]
                       (take 12 (drop 18 r))))))
