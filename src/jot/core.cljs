
(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [<? dochan go-catch]])
  (:require [clojure.data :refer [diff]]
            [cljs.core.async :as async :refer [<! chan put! timeout]]
            [cljsjs.fastclick :as fastclick]
            [reagent.core :as reagent]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [re-frame.db :refer [app-db]]
            [jot.components :as components]
            [jot.connectivity :as connectivity]
            [jot.data :as data]
            [jot.dropbox :as dropbox]
            [jot.routing :as routing]
            [jot.storage :as storage]
            [jot.sync :as sync]
            [jot.util :as util]))

(enable-console-print!)
(js/FastClick.attach (.. js/document -body))

;; pull

(defonce listener (atom nil))

(defn on-change [{:keys [id deleted? text timestamp] :as note}]
  (println "(on-change)" note)
  (if deleted?
    (dispatch [:dissoc-note note])
    (dispatch [:assoc-note {:id id
                            :text text
                            :timestamp timestamp}])))

(defn cursor-changed? [prev-listener listener]
  (not= (sync/cursor prev-listener)
        (sync/cursor listener)))

(defn cursor-watcher [_ _ prev-listener listener]
  (if (cursor-changed? prev-listener listener)
    (let [cursor (sync/cursor listener)]
      (println "(cursor)" cursor)
      (storage/save! :cursor cursor))))

(defn start-syncing []
  (go
    (when (<? (sync/restore!))
      (dispatch-sync [:update-db {:syncing? true}])
      (when-not @listener
        (println "(sync)" :start)
        (reset! listener (sync/listen (storage/load :cursor) on-change))
        (add-watch (:state @listener) :cursor-watcher cursor-watcher)))))

(defn stop-syncing []
  (when @listener
    (println "(sync)" :stop)
    (remove-watch (:state @listener) :cursor-watcher)
    (sync/stop-listening @listener)
    (reset! listener nil)))

;; push

(defonce push-ch (chan))

(defn push-dirty-notes!
  ([]
   (push-dirty-notes! @app-db))
  ([db]
   (doseq [note (data/dirty-notes db)]
     (println "(dirty)" note)
     (put! push-ch note))))

(defn push! [{:keys [id deleted? volatile?] :as note}]
  (go-catch
   (when (connectivity/online?)
     (println "(push)" note)
     (if deleted?
       (if volatile?
         (dispatch [:dissoc-note note])
         (<? (sync/delete! note)))
       (<? (sync/write! note))))))

(defn notes-changed? [prev-db db]
  (not= (data/all-notes prev-db)
        (data/all-notes db)))

(defonce notes-changes
  (-> (util/watch-chan app-db)
      (async/pipe (async/chan 1 (comp (filter #(apply notes-changed? %))
                                      (map last))))
      (util/debounce 1000)))

;; main

(defn render []
  (reagent/render-component [components/app]
                            (js/document.getElementById "app")))

(defn main []
  (dispatch-sync [:initialize {:notes (or (storage/load :notes) {})}])
  (routing/start-history!)
  (render)

  (go
    (dochan [db notes-changes]
      (storage/save! :notes (data/all-notes db))
      (push-dirty-notes! db)))

  (go
    (dochan [note push-ch]
      (<? (push! note))))

  (go
    (let [ch (connectivity/channel)]
      (dochan [{:keys [online?]} ch]
        (println "(online)" online?)
        (if online?
          (do
            (start-syncing)
            (go
              (<! (timeout 1000))
              (push-dirty-notes!)))
          (stop-syncing))))))
