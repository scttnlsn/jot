(ns jot.controllers.storage
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [tailrecursion.cljson :refer [clj->cljson cljson->clj]]
            [jot.util :as util]))

(defn save! [key data]
  (.setItem js/localStorage (name key) (clj->cljson data)))

(defn load [key]
  (cljson->clj (.getItem js/localStorage (name key))))

(defn listen [key ch]
  (go
   (while true
     (let [[_ data] (<! ch)]
       (save! key data)))))
