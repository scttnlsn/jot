(ns jot.macros)

(defmacro <? [expr]
  `(jot.util/throw-error (cljs.core.async/<! ~expr)))

(defmacro go-catch [& body]
  `(cljs.core.async.macros/go
    (try
      ~@body
      (catch js/Error e#
        e#))))

(defmacro dochan [[binding ch] & body]
  `(loop []
     (if-let [~binding (<? ~ch)]
       (do
         ~@body
         (recur)))))
