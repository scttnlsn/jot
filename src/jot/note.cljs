(ns jot.note
  (:require [clojure.string :as string]
            [jot.util :as util]))

(defn- substring? [substring s]
  (if (nil? s)
    false
    (not (= (.indexOf s substring) -1))))

(defn- present? [s]
  (not (string/blank? s)))

(defn- parse-lines [note]
  (vec (filter present? (string/split-lines (:text note)))))

(defn init []
  {:timestamp (js/Date.)
   :path (str "/" (util/uuid))
   :text ""})

(defn title [note]
  (let [lines (parse-lines note)]
    (if (> (count lines) 0)
      (first lines)
      "Untitled")))

(defn summary [note]
  (let [lines (parse-lines note)]
    (if (> (count lines) 1)
      (nth lines 1)
      "")))

(defn date-str [note]
  (-> note
      (:timestamp)
      (.toISOString)
      (.split "T")
      (first)))

(defn tags [note]
  (vec (re-seq #"[#]+[A-Za-z0-9\-_]+" (:text note))))

(defn matches? [note term]
  (substring? (string/lower-case term) (string/lower-case (:text note))))

(defn matching [notes term]
  (filter #(matches? % term) notes))

(defn sorted [notes]
  (vec
    (sort
      #(compare (:timestamp %2) (:timestamp %1))
      notes)))