(ns jot.controllers.sync
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [<? go-catch]])
  (:require [cljs.core.async :as async :refer [>! close! chan put! timeout]]
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
   :timestamp (:timestamp change)
   :deleted (:deleted change)})

(defn- dispatch [name ch state context]
  (go
   (let [sync-ch (get-in state [:control :sync-ch])]
     (try
       (let [res (<? ch)]
         (>! sync-ch [[name :success] (merge context res)]))
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
  (let [sync-ch (get-in state [:control :sync-ch])
        syncing (dropbox/authenticated? client)]
    (if syncing
      (do
        (put! sync-ch [:push-all-dirty {}])
        (put! sync-ch [:poll {}])))
    (-> state
        (assoc :syncing syncing))))

(defmethod sync! [:restore :error]
  [name params state]
  (-> state
      (assoc :syncing (dropbox/authenticated? client))))

(defmethod sync! :push
  [name {:keys [id deleted volatile] :as item} state]
  (if (and (:syncing state)
           (:online state))
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
  state)

(defmethod sync! :poll
  [name params state]
  (let [sync-ch (get-in state [:control :sync-ch])
        cursor (:cursor state)]
    (if cursor
      (dispatch :poll (dropbox/poll client cursor) state {:cursor cursor})
      (put! sync-ch [[:poll :success] {:has-changes true
                                       :retry-timeout 0
                                       :cursor nil}])) ; FIXME should cursor be included here?
    state))

(defmethod sync! [:poll :success]
  [name {:keys [has-changes retry-timeout cursor]} state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (if has-changes
      (put! sync-ch [:pull {:cursor cursor}])
      (go
       (<! (timeout retry-timeout))
       (>! sync-ch [:poll {}])))
    state))

(defmethod sync! [:poll :error]
  [name params state]
  state)

(defmethod sync! :pull
  [name {:keys [cursor]} state]
  (dispatch :pull (dropbox/pull client cursor) state {}))

(defn- read-changes [changes]
  (let [ch (chan)]
    (go-catch
     (doseq [{:keys [path deleted] :as change} changes]
       (if deleted
         (>! ch change)
         (let [res (<? (dropbox/read client path))]
           (>! ch (merge change res)))))
     (close! ch))
    (async/pipe (async/reduce conj [] ch)
                (chan 1 (map (fn [changes] {:changes changes}))))))

(defmethod sync! [:pull :success]
  [name {:keys [changes cursor pull-again]} state]
  (let [sync-ch (get-in state [:control :sync-ch])]
    (put! sync-ch [:read {:changes changes
                          :cursor cursor}])
    (if pull-again
      (put! sync-ch [:pull {:cursor cursor}]))
    state))

(defmethod sync! :read
  [name {:keys [changes cursor]} state]
  (dispatch :read (read-changes changes) state {:cursor cursor}))

(defn- hash-by-id [items]
  (into {} (map (fn [{:keys [id] :as item}]
                  [id item]) items)))

(defmethod sync! [:read :success]
  [name {:keys [changes cursor]} state]
  (let [sync-ch (get-in state [:control :sync-ch])
        items (map change->data changes)
        [updated deleted] (partition-by :deleted items)]
    (put! sync-ch [:poll {}])
    (-> state
        (assoc :notes (-> (:notes state)
                          (merge (hash-by-id updated))
                          (dissoc (vec (map :id deleted)))))
        (assoc :cursor cursor))))

(defmethod sync! [:read :error]
  [name params state]
  state)

(defn restore [sync-ch]
  (put! sync-ch [:restore {}]))

(defn listen [src-ch sync-ch]
  (let [debounced-ch (util/debounce src-ch 500)]
    (go
     (while true
       (let [[_ items] (<! debounced-ch)]
         (doseq [item (dirty (vals items))]
           (put! sync-ch [:push item])))))))
