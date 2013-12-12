(ns reindeer-maze.util)

(defn indexed
  [coll]
  (map vector (range) coll))

(defn make-odd
  [x]
  {:post [(odd? %)]}
  (let [x-int (int x)]
    (if (odd? x-int)
      x-int
      (inc x-int))))

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
