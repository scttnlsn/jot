(ns jot.storage
  (:require [cljs.core.async :as async :refer [chan put!]]
            [alandipert.storage-atom :refer [local-storage]]))

(defn- filter-dirty [state]
  (filter #(:dirty %) (vals @state)))

(defn store
  ([name]
    (store name {:key :id}))
  ([name {:keys [key]}]
    (let [state (local-storage (atom {}) name)
          dirty (filter-dirty state)
          changes (chan)]
      (doseq [data dirty]
        (put! changes data))
      {:state state
       :changes changes
       :key key})))

(defn all [{:keys [state]}]
  @state)

(defn lookup [store key]
  (get (all store) key))

(defn save! [{:keys [key state]} data]
  (swap! state assoc (get data key) data)
  data)

(defn delete! [{:keys [key state]} data]
  (swap! state dissoc (get data key)))

; local

(defn local-save! [{:keys [changes] :as store} data]
  (let [dirty-data (assoc data :dirty true)
        result (save! store dirty-data)]
    (put! changes result)
    result))

(defn local-create! [store data]
  (local-save! store (assoc data :volatile true)))

(defn local-delete! [store data]
  (local-save! store (assoc data :deleted true)))