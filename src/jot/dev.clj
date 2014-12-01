(ns jot.dev
  (:require [cemerick.piggieback :as piggieback]
            [weasel.repl.websocket :as weasel]))

(defn browser-repl []
  (piggieback/cljs-repl
    :repl-env
    (weasel/repl-env :ip "0.0.0.0" :port 9001)))

(comment (jot.dev/browser-repl))
