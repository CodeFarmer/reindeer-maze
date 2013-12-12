(ns reindeer-maze.render
  (require [clojure.pprint :refer [pprint]]
           [reindeer-maze.navigation :refer [wall-coordinates]]
           [reindeer-maze.util :refer :all]
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

(defn quil-tree
  [x y size]
  (let [left (* size x)
        top (* size y)
        right (+ left size)
        bottom (+ top size)
        midline (+ top (* 0.66 size))]
    (triangle left bottom
              right bottom
              (/ (+ left right) 2) top)
    (triangle left midline
              right midline
              (/ (+ left right) 2) top)))

(defn quil-block
  [x y size]
  (rect (* size x)
        (* size y)
        size
        size))

(defn quil-dot
  [x y size]
  (ellipse (+ (* size x)
              (/ size 2))
           (+ (* size y)
              (/ size 2))
           size
           size))
