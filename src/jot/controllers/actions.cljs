(ns jot.controllers.actions
  (:require [jot.note :as note]
            [jot.routes :as routes]
            [jot.util :as util]))

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
    (util/redirect history (routes/note-path {:id id}))
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
  (let [history (get-in state [:control :history])]
    (util/redirect history (routes/notes-path))
    (-> state
        (assoc :notes (dissoc notes (:id note))))))

(defmethod action! :select-note
  [name {:keys [note]} state]
  (let [history (get-in state [:control :history])]
    (util/redirect history (routes/note-path {:id (:id note)}))
    state))

(defmethod action! :scroll
  [name {:keys [offset]} state]
  (-> state
      (assoc :scroll offset)))

(defmethod action! :default
  [name params state]
  state)
