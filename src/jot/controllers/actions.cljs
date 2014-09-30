(ns jot.controllers.actions
  (:require [cljs.core.async :refer [put!]]
            [jot.note :as note]
            [jot.routes :as routes]))

(defmulti action!
  (fn [name params state] name))

(defmethod action! :select-tag
  [name {:keys [tag]} state]
  (assoc state :search tag))

(defmethod action! :search
  [name {:keys [term]} state]
  (assoc state :search term))

(defmethod action! :create-note
  [name params state]
  (let [history (get-in state [:control :history])
        new-note (note/init)
        id (:id new-note)]
    (routes/redirect history (routes/note-path {:id id}))
    (-> state
        (assoc-in [:notes id] new-note)
        (assoc-in [:notes id :dirty] true)
        (assoc-in [:notes id :volatile] true))))

(defmethod action! :update-note
  [name {:keys [note text]} state]
  (let [id (:id note)]
    (-> state
        (assoc-in [:notes id :text] text)
        (assoc-in [:notes id :dirty] true))))

(defmethod action! :delete-note
  [name {:keys [note]} {:keys [notes] :as state}]
  (let [history (get-in state [:control :history])
        id (:id note)]
    (routes/redirect history (routes/notes-path))
    (-> state
        (assoc-in [:notes id :deleted] true)
        (assoc-in [:notes id :dirty] true))))

(defmethod action! :select-note
  [name {:keys [note]} state]
  (let [history (get-in state [:control :history])]
    (routes/redirect history (routes/note-path {:id (:id note)}))
    state))

(defmethod action! :scroll
  [name {:keys [offset]} state]
  (-> state
      (assoc :scroll offset)))

(defmethod action! :toggle-sync
  [name params state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (if (:syncing state)
      (put! sync-ch [:stop params])
      (put! sync-ch [:start params])))
  state)

(defmethod action! :default
  [name params state]
  state)
