(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :as async :refer [chan]]
            [om.core :as om :include-macros true]
            [jot.components.app :as app]
            [jot.controllers.navigation :as navigation]
            [jot.controllers.actions :as actions]
            [jot.controllers.connectivity :as connectivity]
            [jot.controllers.storage :as storage]
            [jot.routes :as routes]
            [jot.state :as state]
            [jot.util :as util]))

(enable-console-print!)
(.attach js/FastClick (.. js/document -body))
(def log-channels? true)

(defn action-handler [[name params] state]
  (if log-channels?
    (println "action:" name params state))
  (swap! state (partial actions/action! name params)))

(defn connectivity-handler [value state]
  (if log-channels?
    (println "connectivity:" value state))
  (swap! state (partial connectivity/connectivity! value)))

(defn nav-handler [[name params] state]
  (if log-channels?
    (println "nav:" name params state))
  (swap! state (partial navigation/navigate! name params)))

(defn install-om! [state shared]
  (om/root app/app
           state
           {:target (.getElementById js/document "app")
            :shared shared}))

(defn main []
  (let [history (routes/create-history)
        action-ch (chan)
        connectivity-ch (chan)
        nav-ch (chan)
        state (atom (-> (state/initial-state)
                        (assoc :notes (storage/load :notes))
                        (assoc-in [:control :action-ch] action-ch)
                        (assoc-in [:control :connectivity-ch] connectivity-ch)
                        (assoc-in [:control :nav-ch] nav-ch)
                        (assoc-in [:control :history] history)))]

    (connectivity/listen connectivity-ch)
    (storage/listen :notes (util/watch state [:notes]))

    (routes/define-routes! nav-ch)
    (routes/start-history! history)

    (install-om! state {:action-ch action-ch})

    (go
     (while true
       (alt!
        action-ch ([value] (action-handler value state))
        connectivity-ch ([value] (connectivity-handler value state))
        nav-ch ([value] (nav-handler value state)))))))

(main)
