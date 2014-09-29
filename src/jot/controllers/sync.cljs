(ns jot.controllers.sync
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [<?]])
  (:require [cljs.core.async :as async :refer [>! put!]]
            [jot.dropbox :as dropbox]
            [jot.util :as util]))

(def dropbox-key "h3kdlj1z821oa5q")
(def client (dropbox/create-client dropbox-key))

(defn- dirty [items]
  (filter :dirty items))

(defn- data->change [data]
  {:path (str "/" (:id data))
   :data (:text data)})

(defn- change->data [change]
  {:id (subs (:path change) 1)
   :text (:data change)
   :timestamp (:timestamp change)})

(defn- dispatch [name ch state context]
  (go
   (let [sync-ch (get-in state [:control :sync-ch])]
     (try
       (let [res (<? ch)]
         (>! sync-ch [[name :success] (assoc context :res res)]))
       (catch js/Object err
         (>! sync-ch [[name :error] err])))))
  state)

(defmulti sync!
  (fn [name params state] name))

(defmethod sync! :start
  [name params state]
  (dispatch :start (dropbox/authenticate client true) state {}))

(defmethod sync! [:start :success]
  [name params state]
  (-> state
      (assoc :syncing true)))

(defmethod sync! :stop
  [name params state]
  (dispatch :stop (dropbox/logout client) state {}))

(defmethod sync! [:stop :success]
  [name params state]
  (-> state
      (assoc :syncing false)))

(defmethod sync! :restore
  [name params state]
  (dispatch :restore (dropbox/authenticate client false) state {}))

(defmethod sync! [:restore :success]
  [name params state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (put! sync-ch [:push-all-dirty {}]))
  (-> state
      (assoc :syncing (dropbox/authenticated? client))))

(defmethod sync! [:restore :error]
  [name params state]
  (-> state
      (assoc :syncing (dropbox/authenticated? client))))

(defmethod sync! :push
  [name {:keys [id deleted volatile] :as item} state]
  (if (:syncing state)
    (let [sync-ch (get-in state [:control :sync-ch])]
      (if deleted
        (if volatile
          (util/dissoc-in state [:notes id])
          (do
            (put! sync-ch [:delete item])
            state))
        (do
          (put! sync-ch [:write item])
          state)))
    state))

(defmethod sync! :push-all-dirty
  [name params state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (doseq [item (dirty (vals (:notes state)))]
      (put! sync-ch [:push item])))
  state)

(defmethod sync! :write
  [name item state]
  (let [{:keys [path data]} (data->change item)]
    (dispatch :write (dropbox/write client path data) state item)))

(defmethod sync! [:write :success]
  [name {:keys [id]} state]
  (-> state
      (util/dissoc-in [:notes id :dirty])
      (util/dissoc-in [:notes id :volatile])))

(defmethod sync! [:write :error]
  [name err state]
  (println "Sync write error")
  state)

(defmethod sync! :delete
  [name item state]
  (let [{:keys [path]} (data->change item)]
    (dispatch :delete (dropbox/delete client path) state item)))

(defmethod sync! [:delete :success]
  [name {:keys [id]} state]
  (-> state
      (util/dissoc-in [:notes id])))

(defmethod sync! [:delete :error]
  [name err state]
  (println "Sync delete error")
  state)

(defn restore [sync-ch]
  (put! sync-ch [:restore {}]))

(defn listen [src-ch sync-ch]
  (go
   (while true
     (let [[_ items] (<! (util/debounce src-ch 500))]
       (doseq [item (dirty (vals items))]
         (put! sync-ch [:push item]))))))
