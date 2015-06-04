(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [<? dochan]])
  (:require [cljs.core.async :refer [<! put!]]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync]]
            [jot.components :as components]
            [jot.data :as data]
            [jot.dropbox :as dropbox]
            [jot.routing :as routing]
            [jot.sync :as sync]))

(enable-console-print!)

(dispatch-sync [:initialize])
(routing/start-history!)
(reagent/render-component [components/app]
                          (js/document.getElementById "app"))

(def state (atom nil))

(go
  (when (<? (sync/restore!))
    (dispatch-sync [:initialize {:syncing? true}])
    (let [listener (sync/listen nil)]
      (reset! state listener)
      (dochan [result (:results listener)]
              (doseq [{:keys [path deleted? timestamp]} (:changes result)]
                (if deleted?
                  (println "FIXME handle deleted")
                  (let [note {:id (subs path 1)
                              :text (<? (sync/read path))
                              :timestamp timestamp}]
                    (dispatch [:load-note note]))))))))

(defn stop []
  (if @state
    (sync/stop-listening @state)))
