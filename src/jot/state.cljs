(ns jot.state)

(defn initial-state []
  {:route nil
   :notes {}
   :search ""

   :online false

   :control {:action-ch nil
             :connectivity-ch nil
             :nav-ch nil
             :history nil}})
