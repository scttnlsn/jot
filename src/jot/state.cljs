(ns jot.state)

(defn initial-state []
  {:route nil
   :notes {}
   :search ""

   :online false
   :syncing false
   :cursor nil

   :control {:action-ch nil
             :connectivity-ch nil
             :nav-ch nil
             :sync-ch nil
             :history nil}})
