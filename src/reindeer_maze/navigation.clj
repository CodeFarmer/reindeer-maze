(ns reindeer-maze.navigation
  (:require [reindeer-maze.util :refer :all]))

(defn count-steps-east
  [row x]
  {:post [(<= 0 %)]}
  ;; Count the nils after the current position.
  (count (take-while nil?
                     (drop (inc x)
                           row))))

(defn count-steps-west
  [row initial-x]
  (count-steps-east (reverse row)
                     (- (dec (count row)) initial-x)))

(defn possible-moves
  [maze [x y :as point]]
  (let [row (nth maze y)
        column (map #(nth % x) maze)]
    {:north (count-steps-west column y)
     :south (count-steps-east column y)
     :west  (count-steps-west row x)
     :east  (count-steps-east row x)}))

(defn random-spot
  [maze]
  (let [rand-rownum (rand-int (count maze))
        random-row  (nth maze rand-rownum)
        rand-colnum (rand-int (count random-row))
        random-cell (nth random-row rand-colnum)]
    {:position [rand-colnum rand-rownum]
     :cell random-cell}))

(defn random-free-position
  [maze]
  (let [{cell :cell
         position :position} (random-spot maze)]
    (if (nil? cell)
      position
      (recur maze))))

(defn path-between
  "If there is a straight-line, uninterrupted path between [x1 y1] and [x2 y2], returns the direction as a keyword.
   Otherwise, returns nil."
  [[x1 y1] [x2 y2] maze]
  (cond
   (and (= x1 x2)
        (= y1 y2)) :hit
   (and (not= x1 x2)
        (not= y1 y2)) nil
   
   (= y1 y2) (let [moves (possible-moves maze [x1 y1])]
               (cond
                (<= 0 (- x2 x1) (:east moves)) :east
                (<= 0 (- x1 x2) (:west moves)) :west
                ))
   (= x1 x2) (let [moves (possible-moves maze [x1 y1])]
               (cond
                (<= 0 (- y1 y2) (:north moves)) :north
                (<= 0 (- y2 y1) (:south moves)) :south
                ))))

(defn wall-coordinates
  [maze]
  (for [[y row]  (indexed maze)
        [x cell] (indexed row)
        :when (not (nil? cell))]
    [x y]))
