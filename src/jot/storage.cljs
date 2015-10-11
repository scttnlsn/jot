(ns jot.storage
  (:require [cljs.reader :as reader]))

(defn save! [key data]
  (.setItem js/localStorage (name key) (prn-str data)))

(defn load [key]
  (let [s (.getItem js/localStorage (name key))]
    (if s
      (reader/read-string s))))
