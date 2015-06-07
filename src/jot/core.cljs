(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [run!]]
                   [jot.macros :refer [<? dochan]])
  (:require [cljs.core.async :refer [<! chan put!]]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
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

(def listener (atom nil))

(defn on-change [{:keys [id deleted? text timestamp] :as note}]
  (if deleted?
    (dispatch [:dissoc-note ])
    (dispatch [:load-note {:id id
                           :text text
                           :timestamp timestamp}])))

(defn start-syncing []
  (reset! listener (sync/listen nil on-change)))

(defn stop-syncing []
  (if @listener
    (sync/stop-listening @listener)))

(go
  (when (<? (sync/restore!))
    (dispatch-sync [:initialize {:syncing? true}])
    (start-syncing)))

(def dirty-notes (subscribe [:dirty-notes]))

(def push-ch (chan))

(run!
 (doseq [note @dirty-notes]
   (put! push-ch note)))

(go
  (dochan [{:keys [id] :as note} push-ch]
          (case (<? (sync/push! note))
            :update
            (dispatch [:update-note id {:dirty? false
                                        :volatile? false}])

            :delete
            (dispatch [:dissoc-note note]))))
