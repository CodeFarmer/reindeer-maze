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
