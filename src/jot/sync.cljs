(ns jot.sync
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [jot.macros :refer [<?]])
  (:require [cljs.core.async :as async :refer [>! <! alts! chan close! pipe put! take! timeout]]
            [jot.dropbox :as dropbox]
            [jot.util :as util]))

(defn- pull-chan [client cursor]
  (util/async-chan dropbox/pull client cursor))

(defn- poll-chan [client cursor]
  (util/async-result-chan dropbox/poll client cursor))

(defn- read-chan [client path]
  (util/async-chan dropbox/read client path))

(defn- delete-chan [client path]
  (util/async-chan dropbox/delete client path))

(defn- write-chan [client path data]
  (util/async-chan dropbox/write client path data))

(defn pull [client cursor]
  (let [out-changes (chan)
        out-cursor (chan)]
    (go
      (loop [cursor cursor]
        (let [[pull-result] (<? (pull-chan client cursor))
              {:keys [changes cursor pull-again]} pull-result]
          (doseq [change changes]
            (if (:deleted change)
              (>! out-changes (assoc change :cursor cursor))
              (let [[content] (<? (read-chan client (:path change)))]
                (>! out-changes (-> change
                                    (assoc :cursor cursor)
                                    (merge content))))))
          (if pull-again
            (recur cursor)
            (do
              (close! out-changes)
              (>! out-cursor cursor)
              (close! out-cursor))))))
    [out-changes out-cursor]))

(defn push [client change]
  (let [out (chan)]
    (go
      (if (:deleted change)
        (if-not (:volatile change)
          (<? (delete-chan client (:path change))))
        (<? (write-chan client (:path change) (:data change))))
      (>! out (dissoc change :dirty :volatile))
      (close! out))
    out))

(defn poll [client cursor]
  (let [out (chan)
        stop-ch (chan)
        stop #(put! stop-ch true)
        return #(go
                  (>! out %)
                  (close! out)
                  (close! stop-ch))]
    (go
      (if cursor
        (let [[poll-ch xhr] (poll-chan client cursor)]
          (go
            (if (<! stop-ch)
              (do
                (.abort xhr))))
          (let [[poll-result] (<? poll-ch)
                {:keys [has-changes retry-timeout]} poll-result]
            (return {:has-changes has-changes :retry-timeout retry-timeout :cursor cursor})))
        (return {:has-changes true :retry-timeout 0 :cursor cursor})))
    [out stop]))

(defn start [client cursor]
  (let [out (chan)
        stop-ch (chan)
        stop #(do
                (put! stop-ch true)
                (close! stop-ch))]
    (go
      (loop [cursor cursor]
        (let [[poll-ch stop-poll] (poll client cursor)
              exit-ch (chan)]
          (go
            (let [[val ch] (alts! [stop-ch exit-ch])]
              (if val
                (stop-poll))))
          (let [{:keys [has-changes retry-timeout cursor] :as result} (<? poll-ch)]
            (if result
              (do
                (if has-changes
                  (let [[changes-ch cursor-ch] (pull client cursor)]
                    (pipe changes-ch out false)
                    (>! exit-ch false)
                    (<! (timeout retry-timeout))
                    (recur (<? cursor-ch))))
                (do
                  (>! exit-ch false)
                  (<! (timeout retry-timeout))
                  (recur cursor))))))))
    [out stop]))