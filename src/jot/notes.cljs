(ns jot.notes
  (:require [clojure.string :as string]))

(defn nonempty-lines [{:keys [text]}]
  (->> (string/split-lines text)
       (filter #(not (string/blank? %)))
       (vec)))

(defn title [note]
  (let [lines (nonempty-lines note)]
    (if (> (count lines) 0)
      (first lines)
      "Untitled")))

(defn summary [note]
  (let [lines (nonempty-lines note)]
    (if (> (count lines) 1)
      (nth lines 1)
      "")))

(defn date-string [{:keys [timestamp]}]
  (-> timestamp
      (.toISOString)
      (.split "T")
      (first)))

(defn tags [{:keys [text]}]
  (vec (map #(nth % 1)
            (re-seq #"[\s]([#]+[A-Za-z0-9\-_]+)" text))))

(defn- substring? [substring s]
  (if (nil? s)
    false
    (not (= (.indexOf s substring) -1))))

(defn matches? [{:keys [text]} term]
  (substring? (string/lower-case term)
              (string/lower-case text)))
