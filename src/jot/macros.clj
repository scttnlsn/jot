(ns jot.macros)

(defmacro <? [expr]
  `(jot.util/throw-error (cljs.core.async/<! ~expr)))

(defmacro dochan [[binding ch] & body]
  `(loop []
     (if-let [~binding (<? ~ch)]
       (do
         ~@body
         (recur)))))
