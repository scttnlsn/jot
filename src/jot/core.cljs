(ns jot.core
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [dochan]])
  (:require [cljs.core.async :as async :refer [<! chan timeout]]
            [alandipert.storage-atom :refer [local-storage]]
            [om.core :as om :include-macros true]
            [jot.connectivity :as connectivity]
            [jot.note :as jn]
            [jot.session :as session]
            [jot.sync :as sync]
            [jot.ui :as ui]
            [jot.util :as util]
            [jot.storage :as storage]
            [jot.routes :as routes]))

(enable-console-print!)
(.attach js/FastClick (.. js/document -body))

; app

(def note-storage (storage/store :notes {:key :path}))
(def note-changes (:changes note-storage))

(def actions (chan))

(def app-state (atom {:notes (storage/all note-storage)
                      :term ""
                      :scroll 0
                      :route {:name :note-list}}))

(om/root ui/root app-state
  {:init-state {:actions actions}
   :target (.getElementById js/document "app")})

; actions

(defn update! [note]
  (let [path (:path note)]
    (swap! app-state assoc-in [:notes path] (storage/save! note-storage note))))

(defn delete! [note]
  (let [path (:path note)]
    (storage/delete! note-storage note)
    (swap! app-state update-in [:notes] dissoc path)))

(defmulti perform-action!
  (fn [action] (:type action)))

(defmethod perform-action! :select [action]
  (let [note (:note action)
        path (:path note)]
    (util/navigate (str "#/notes" path))))

(defmethod perform-action! :create [action]
  (let [note (jn/init)
        path (:path note)]
    (swap! app-state assoc-in [:notes path] (storage/local-create! note-storage note))
    (util/navigate (str "#/notes" path))))

(defmethod perform-action! :save [action]
  (let [note (:note action)
        path (:path note)]
    (swap! app-state assoc-in [:notes path] (storage/local-save! note-storage note))))

(defmethod perform-action! :delete [action]
  (let [note (:note action)
        path (:path note)]
    (if (js/confirm "Are you sure?")
      (do
        (swap! app-state #(update-in % [:notes] dissoc path))
        (storage/local-delete! note-storage note)
        (util/navigate "#/")))))

(defmethod perform-action! :close [action]
  (util/navigate "#/"))

(defmethod perform-action! :settings [action]
  (util/navigate "#/settings"))

(defmethod perform-action! :toggle-connection [action]
  (if (:connected @app-state)
    (do
      (swap! app-state assoc :connected false)
      (session/end))
    (do
      (swap! app-state assoc :connected true)
      (session/connect))))

(go
  (dochan [action actions]
    (perform-action! (assoc action :app-state app-state))))

; sync

(defn- change->note [change]
  (-> change
      (select-keys [:path :timestamp])
      (assoc :text (:data change))))

(defn- note->change [note]
  (-> note
      (select-keys [:path :timestamp :volatile :deleted])
      (assoc :data (:text note))))

(def metadata (local-storage (atom {}) :metadata))

(def stopper (atom nil))

(defn start-sync []
  (let [cursor (:cursor @metadata)
        [changes stop] (sync/start session/dropbox-client cursor)]
    (reset! stopper stop)
    (go
      (dochan [note note-changes]
        (let [push-ch (sync/push session/dropbox-client (note->change note))
              change (<! push-ch)]
          (if (:deleted change)
            (delete! (change->note change))
            (update! (change->note change))))))
    (go
      (dochan [change changes]
        (if (:deleted change)
          (delete! (change->note change))
          (update! (change->note change)))
        (swap! metadata assoc :cursor (:cursor change))))))

(defn stop-sync []
  (if @stopper
    (@stopper)))

(add-watch connectivity/state :core
  (fn [_ _ _ {:keys [online]}]
    (if online
      (do
        (go
          (<! (timeout 1000))
          (session/start)))
      (stop-sync))))

(add-watch session/state :core
  (fn [_ _ _ {:keys [active error]}]
    (if error
      (do
        (stop-sync)
        (.log js/console error))
      (if active
        (start-sync)
        (stop-sync)))))

(if (connectivity/online?)
  (session/start))

(swap! app-state assoc :connected (session/active?))

(routes/define-routes! app-state)