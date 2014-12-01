(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go alt!]])
  (:require [cljs.core.async :as async :refer [chan]]
            [om.core :as om :include-macros true]
            [weasel.repl :as ws-repl]
            [jot.components.app :as app]
            [jot.controllers.navigation :as navigation]
            [jot.controllers.actions :as actions]
            [jot.controllers.connectivity :as connectivity]
            [jot.controllers.storage :as storage]
            [jot.controllers.sync :as sync]
            [jot.dropbox :as dropbox]
            [jot.routes :as routes]
            [jot.state :as state]
            [jot.util :as util]))

(enable-console-print!)
(.attach js/FastClick (.. js/document -body))

(defn- channel-log [title name params state]
  (if util/logging-enabled?
    (do
      (.log js/console
            (str "--- %c " title ":")
            "font-weight: bold"
            (str name))
      (.log js/console (clj->js params))
      (.log js/console (clj->js @state)))))

(defn action-handler [[name params] state]
  (channel-log "action" name params state)
  (swap! state (partial actions/action! name params)))

(defn connectivity-handler [params state]
  (channel-log "connectivity" nil params state)
  (swap! state (partial connectivity/connectivity! params)))

(defn nav-handler [[name params] state]
  (channel-log "nav" name params state)
  (swap! state (partial navigation/navigate! name params)))

(defn sync-handler [[name params] state]
  (channel-log "sync" name params state)
  (swap! state (partial sync/sync! name params)))

(defn install-om! [state shared]
  (om/root app/app
           state
           {:target (.getElementById js/document "app")
            :shared shared}))

(defn main []
  (let [history (routes/create-history)
        action-ch (chan)
        connectivity-ch (connectivity/channel)
        nav-ch (chan)
        sync-ch (chan)
        state (atom (-> (state/initial-state)
                        (assoc :online (connectivity/online?))
                        (assoc :route (routes/default-route))
                        (assoc :notes (storage/load :notes))
                        (assoc :cursor (storage/load :cursor))
                        (assoc :control {:action-ch action-ch
                                         :connectivity-ch connectivity-ch
                                         :nav-ch nav-ch
                                         :sync-ch sync-ch
                                         :history history})))]

    (storage/listen :notes (util/watch state [:notes]))
    (storage/listen :cursor (util/watch state [:cursor]))
    (sync/listen (util/watch state [:notes]) sync-ch)
    (sync/restore sync-ch)
    (routes/define-routes! nav-ch)
    (routes/start-history! history)

    (install-om! state {:action-ch action-ch})

    (go
     (while true
       (alt!
        action-ch ([value] (action-handler value state))
        connectivity-ch ([value] (connectivity-handler value state))
        nav-ch ([value] (nav-handler value state))
        sync-ch ([value] (sync-handler value state)))))))

(main)

(if util/repl-enabled?
  (ws-repl/connect "ws://localhost:9001" :verbose true))
