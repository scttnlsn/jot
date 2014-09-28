(ns jot.components.app
  (:require [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [om.core :as om]
            [jot.components.notes :as notes]
            [jot.components.settings :as settings]))

(defmulti page
  (fn [name params cursor] name))

(defmethod page :notes
  [name params cursor]
  (om/build notes/note-list cursor))

(defmethod page :note
  [name {:keys [id]} cursor]
  (let [note (get-in cursor [:notes id])]
    (om/build notes/note-editor note)))

(defmethod page :settings
  [name params cursor]
  (om/build settings/settings cursor))

(defmethod page nil
  [name params cursor]
  (dom/div nil "Loading..."))

(defcomponent app [cursor owner]
  (render [_]
    (let [[name params] (:route cursor)]
      (page name params cursor))))
