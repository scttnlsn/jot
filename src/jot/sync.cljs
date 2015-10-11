(ns jot.sync
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [<? dochan go-catch]])
  (:require [cljs.core.async :refer [<! >! chan close! timeout put!]]
            [re-frame.core :refer [register-handler]]
            [jot.dropbox :as dropbox]
            [jot.util :as util]))

(def dropbox-key "h3kdlj1z821oa5q")
(def client (dropbox/create-client dropbox-key))

(defn connect! []
  (go-catch
   (<? (last (dropbox/authenticate client true)))
   (dropbox/authenticated? client)))

(defn restore! []
  (go-catch
   (<? (last (dropbox/authenticate client false)))
   (dropbox/authenticated? client)))

(defn disconnect! []
  (go-catch
   (<? (last (dropbox/logout client)))))

(defn read [path]
  (go-catch
   (<? (last (dropbox/read client path)))))

(defn write! [{:keys [id text]}]
  (go-catch
   (let [path (str "/" id)]
     (println "(write)" path text)
     (<? (last (dropbox/write client path text))))))

(defn delete! [{:keys [id]}]
  (go-catch
   (let [path (str "/" id)]
     (println "(delete)" path)
     (<? (last (dropbox/delete client path))))))

;; change tracking

(defmulti handle
  (fn [_ [name]] name))

(defn- track-changes [results on-change]
  (go
    (dochan [result results]
      (doseq [{:keys [path deleted? timestamp]} (:changes result)]
        (let [id (subs path 1)]
          (if deleted?
            (on-change {:id id
                        :deleted? true
                        :timestamp timestamp})
            (on-change {:id id
                        :text (<? (read path))
                        :timestamp timestamp})))))))

(defn listen [cursor on-change]
  (let [control (chan)
        results (chan)
        state (atom {:cursor cursor
                     :client client
                     :results results
                     :control control
                     :listening? true})]
    (track-changes results on-change)
    (go
      (while (:listening? @state)
        (let [args (<! control)]
          (println "(sync)" args)
          (swap! state #(handle % args))))
      (close! control)
      (close! results))
    (put! control [:poll])
    {:results results
     :control control
     :state state}))

(defn stop-listening [{:keys [control]}]
  (put! control [:abort]))

(defn cursor [{:keys [cursor] :as listener}]
  cursor)

;; state transition handlers

(defmethod handle :poll
  [{:keys [client cursor control] :as listener} _]
  (let [[xhr ch] (dropbox/poll client cursor)]
    (go
      (try
        (put! control [:poll-result (<? ch)])
        (catch js/Error error
          (put! control [:poll-error error]))))
    (-> listener
        (assoc :state :polling)
        (assoc :xhr xhr))))

(defmethod handle :poll-result
  [{:keys [control] :as listener} [_ {:keys [has-changes? retry-timeout]}]]
  (if has-changes?
    (put! control [:pull])
    (go
      (<! (timeout retry-timeout))
      (put! control [:poll])))
  (-> listener
      (dissoc :xhr)
      (assoc :state :waiting)))

(defmethod handle :pull
  [{:keys [client cursor control] :as listener} _]
  (let [[xhr ch] (dropbox/pull client cursor)]
    (go
      (try
        (put! control [:pull-result (<? ch)])
        (catch js/Error error
          (put! control [:error error]))))
    (-> listener
        (assoc :state :pulling)
        (assoc :xhr xhr))))

(defmethod handle :pull-result
  [{:keys [control listening? results] :as listener} [_ {:keys [changes cursor pull-again?]}]]
  (put! results {:changes changes :cursor cursor})
  (if pull-again?
    (put! control [:pull])
    (put! control [:poll]))
  (-> listener
      (assoc :cursor cursor)
      (assoc :state :waiting)))

(defmethod handle :abort
  [{:keys [xhr] :as listener} _]
  (if xhr
    (.abort xhr))
  (-> listener
      (dissoc :xhr)
      (assoc :listening? false)))

(defmethod handle :error
  [listener [_ error]]
  (-> listener
      (dissoc :xhr)
      (assoc :state :error)
      (assoc :error error)))
