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
  (let [result {:path (.-path change) :deleted false :timestamp nil}]
    (if (.-wasRemoved change)
      (assoc result :deleted true)
      (assoc result :timestamp (-> change .-stat .-clientModifiedAt)))))

(defn- parse-pull-result [result]
  {:changes (map parse-change (js->clj (.-changes result)))
   :cursor (.-cursorTag result)
   :pull-again (.-shouldPullAgain result)})

(defn- parse-poll-result [result]
  {:has-changes (.-hasChanges result)
   :retry-timeout (* 1000 (.-retryAfter result))})

(defn client [key]
  (let [Client (.-Client js/Dropbox)]
    (Client. #js{:key key})))

(defn pull [client cursor cb]
  (.pullChanges client cursor (fn [err result]
    (if err
      (cb err)
      (cb nil (parse-pull-result result))))))

(defn poll [client cursor cb]
  (.pollForChanges client cursor (fn [err result]
    (if err
      (cb err)
      (cb nil (parse-poll-result result))))))

(defn read [client path cb]
  (.readFile client path (fn [err data]
    (if err
      (cb err)
      (cb nil {:data data})))))

(defn write [client path data cb]
  (.writeFile client path data (fn [err]
    (if err
      (cb err)
      (cb nil true)))))

(defn delete [client path cb]
  (.remove client path (fn [err]
    (if err
      (cb err)
      (cb nil true)))))

(defn authenticated? [client]
  (.isAuthenticated client))

(defn authenticate [client options cb]
  (.authenticate client options cb))

(defn logout [client cb]
  (.signOut client cb))