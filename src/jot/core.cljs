(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [run!]]
                   [jot.macros :refer [<? dochan go-catch]])
  (:require [clojure.data :refer [diff]]
            [cljs.core.async :refer [<! chan put!]]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [re-frame.db :refer [app-db]]
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
  (println "(on-change)" note)
  (if deleted?
    (dispatch [:dissoc-note note])
    (dispatch [:assoc-note {:id id
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

;; push

(def push-ch (chan))

(defn notes-changed? [prev-db db]
  (let [[a b _] (diff (:notes prev-db) (:notes db))]
    (not= a b)))

(defn dirty-watcher [_ _ prev-db db]
  (if (notes-changed? prev-db db)
    (doseq [note (data/dirty-notes db)]
      (println "(dirty)" note)
      (put! push-ch note))))

(defn push! [{:keys [id deleted? volatile?] :as note}]
  (go-catch
   (if deleted?
     (if volatile?
       (dispatch [:dissoc-note id])
       (<? (sync/delete! note)))
     (<? (sync/write! note)))))

(add-watch app-db :dirty-watcher dirty-watcher)

(go
  (dochan [note push-ch]
    (<? (push! note))))
