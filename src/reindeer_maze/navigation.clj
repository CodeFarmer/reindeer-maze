(ns reindeer-maze.navigation)

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
