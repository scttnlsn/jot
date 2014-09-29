(ns jot.dropbox
  (:require [jot.util :as util]))

(def AuthError (.-AuthError js/Dropbox))
(def ApiError (.-ApiError js/Dropbox))

(extend-protocol util/IError
  AuthError
  (-error? [this] true)

  ApiError
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

(defn- parse-pull-result [result]
  {:changes (map parse-change (js->clj (.-changes result)))
   :cursor (.-cursorTag result)
   :pull-again (.-shouldPullAgain result)})

(defn- parse-poll-result [result]
  {:has-changes (.-hasChanges result)
   :retry-timeout (* 1000 (.-retryAfter result))})

(defn- wrap-callback [cb f]
  (fn [err result]
    (if err
      (cb err)
      (cb nil (f result)))))

(defn authenticated? [client]
  (.isAuthenticated client))

(defn create-client [key]
  (let [Client (.-Client js/Dropbox)]
    (Client. #js {:key key})))

(defn pull [client cursor]
  (util/async-chan
   #(.pullChanges client cursor (wrap-callback % parse-pull-result))))

(defn poll [client cursor]
  (util/async-result-chan
   #(.pollForChanges client cursor (wrap-callback % parse-poll-result))))

(defn read [client path]
  (util/async-chan
   #(.readFile client path (wrap-callback % (fn [data] {:data data})))))

(defn write [client path data]
  (util/async-chan #(.writeFile client path data %)))

(defn delete [client path]
  (util/async-chan
   #(.remove client path %)))

(defn authenticate [client interactive]
  (util/async-chan
   #(.authenticate client #js {:interactive interactive} %)))

(defn logout [client]
  (util/async-chan
   #(.signOut client %)))
