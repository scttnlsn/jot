(ns jot.macros)

(defmacro dochan [[binding ch] & body]
  `(loop []
     (if-let [~binding (cljs.core.async/<! ~ch)]
       (do
         ~@body
         (recur)))))
