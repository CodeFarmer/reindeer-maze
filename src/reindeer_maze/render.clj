(ns reindeer-maze.render
  (require [clojure.pprint :refer [pprint]]
           [quil.core :refer :all]))

(defn cell-as-text
  [cell]
  (if cell
    "@"
    " "))

(defn render-maze-as-text
  [maze]
  (->> maze
       (map (partial map cell-as-text))
       (map (partial apply str))
       pprint))
