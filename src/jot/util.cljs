(ns jot.util
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [jot.macros :refer [<?]])
  (:require [cljs.core.async :as async :refer [alts! >! chan close! put! timeout]])
  (:import goog.Uri))

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

(defn async-chan [f & args]
  (let [ch (chan)
        cb (fn [err res]
             (go
              (if err
                (>! ch err)
                (>! ch res))
              (close! ch)))]
    (apply f (concat args [cb]))
    ch))

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

(defn watch [ref path]
  (let [ch (chan)]
    (add-watch ref (gensym) (fn [_ _ old new]
                              (let [old-value (get-in old path)
                                    new-value (get-in new path)]
                                (if (not (identical? old-value new-value))
                                  (put! ch [old-value new-value])))))
    ch))

(defn dissoc-in
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (assoc m k newmap)
          (dissoc m k)))
      m)
    (dissoc m k)))

(def parsed-uri
  (goog.Uri. (-> (.-location js/window) (.-href))))

(defn parse-bool [string]
  (condp = string
    "true" true
    "false" false
    nil))

(defn parse-qs [param]
  (.getParameterValue parsed-uri param))

(def qs-options
  {:log? (parse-bool (parse-qs "log"))
   :repl? (parse-bool (parse-qs "repl"))
   :dev? (parse-bool (parse-qs "dev"))})

(def logging-enabled? (or (:dev? qs-options)
                          (:log? qs-options)))
(def repl-enabled? (or (:dev? qs-options)
                       (:repl? qs-options)))
