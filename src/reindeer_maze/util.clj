(ns reindeer-maze.util
  (:require [clojure.edn :as edn]))

(defn indexed
  "Given a collection, return ([0 _] [1 _] [2 _])."
  [coll]
  (map vector (range) coll))

(defn make-odd
  "Given a number, make it an odd number."
  [x]
  {:post [(odd? %)]}
  (let [x-int (int x)]
    (if (odd? x-int)
      x-int
      (inc x-int))))

(defn fmap
  "Apply a function to all the values in a map."
  [m f & args]
  (into {} (for [[k v] m]
             [k (apply f v args)])))

(defmacro in-thread
  [& body]
  `(.start (Thread. (fn [] ~@body))))

(defmacro until
  "Repeatedly execute body until test returns false.

   cf. (while)"
  [test & body]
  `(loop []
     (when (not ~test)
       ~@body
       (recur))))

(defn str-to-int
  [str]
  {:post [(int %)]}
  (edn/read-string str))
