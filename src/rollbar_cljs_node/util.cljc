(ns rollbar-cljs-node.util
  #? (:cljs (:require-macros [rollbar-cljs-node.util])))

#? (:clj
    (defmacro when-node [& body]
      `(when (= cljs.core/*target* "nodejs")
         ~@body)))

#? (:clj
    (defmacro when-not-node [& body]
      `(when (not= cljs.core/*target* "nodejs")
         ~@body)))
