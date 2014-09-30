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
  (let [id (util/uuid)]
    {:id id
     :timestamp (js/Date.)
     :text ""}))

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
  (vec
   (map #(nth % 1)
        (re-seq #"[\s]([#]+[A-Za-z0-9\-_]+)" (:text note)))))

(defn matches? [note term]
  (substring? (string/lower-case term) (string/lower-case (:text note))))

(defn matching [notes term]
  (filter #(matches? % term) notes))

(defn filtered [notes]
  (filter (fn [note] (not (:deleted note))) notes))

(defn sorted [notes]
  (vec
   (sort
    #(compare (:timestamp %2) (:timestamp %1))
    notes)))
