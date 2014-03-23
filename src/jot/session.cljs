(ns jot.session
  (:require [cljs.core.async :as async :refer [chan put!]]
            [jot.dropbox :as dropbox]))

(def dropbox-key "h3kdlj1z821oa5q")
(def dropbox-client (dropbox/client dropbox-key))

(def state (atom {:active false :error nil}))

(defn active? []
  (dropbox/authenticated? dropbox-client))

(defn- handle-change [err]
  (if err
    (swap! state assoc :error err)
    (swap! state assoc :active (active?))))

(defn start []
  (dropbox/authenticate dropbox-client #js {:interactive false} handle-change))

(defn connect []
  (dropbox/authenticate dropbox-client #js {} handle-change))

(defn end []
  (dropbox/logout dropbox-client handle-change))
