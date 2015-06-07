(ns jot.dropbox
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [chan close! put!]]
            [cljsjs.dropbox :as dropbox]
            [jot.util :as util]))

(extend-protocol util/IError
  js/Dropbox.AuthError
  (-error? [this] true)

  js/Dropbox.ApiError
  (-error? [this] true))

(defn- parse-change [change]
  (let [result {:path (.-path change)
                :deleted false
                :timestamp nil}]
    (if (.-wasRemoved change)
      (assoc result :deleted true)
      (assoc result :timestamp (-> change
                                   .-stat
                                   .-clientModifiedAt)))))

(defn create-client [key]
  (js/Dropbox.Client. #js {:key key}))

(defn with-chan
  ([f]
   (with-chan f identity))
  ([f xform]
   (let [ch (chan 1 xform)]
     [(f ch) ch])))

(defn chan->cb [ch]
  (fn [err result]
    (put! ch (or err result))
    (close! ch)))

(defn ->pulled-change [result]
  (let [change {:path (.-path result)
                :deleted? false
                :timestamp nil}]
    (if (.-wasRemoved result)
      (assoc change :deleted? true)
      (assoc change :timestamp (-> result
                                   .-stat
                                   .-clientModifiedAt)))))

(defn ->pulled-changes [result]
  {:changes (map ->pulled-change (js->clj (.-changes result)))
   :cursor (.-cursorTag result)
   :pull-again? (.-shouldPullAgain result)})

(defn ->poll-result [result]
  {:has-changes? (.-hasChanges result)
   :retry-timeout (* 1000 (.-retryAfter result))})

(defn pull [client cursor]
  (with-chan
    #(.pullChanges client cursor (chan->cb %))
    (map ->pulled-changes)))

(defn poll [client cursor]
  (if cursor
    (with-chan
      #(.pollForChanges client cursor (chan->cb %))
      (map ->poll-result))
    [nil (go {:has-changes? true
              :retry-timeout 0})]))

(defn read [client path]
  (with-chan
    #(.readFile client path (chan->cb %))))

(defn write [client path data]
  (with-chan
    #(.writeFile client path data (chan->cb %))))

(defn delete [client path]
  (with-chan
    #(.remove client path (chan->cb %))))

(defn authenticate [client interactive]
  (with-chan
    #(.authenticate client #js {:interactive interactive} (chan->cb %))))

(defn authenticated? [client]
  (.isAuthenticated client))

(defn logout [client]
  (with-chan
    #(.signOut client (chan->cb %))))
