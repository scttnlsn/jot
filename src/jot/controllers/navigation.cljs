(ns jot.controllers.navigation)

(defmulti navigate!
  (fn [name params state] name))

(defmethod navigate! :default
  [name params state]
  (-> state
      (assoc :route [name params])))
