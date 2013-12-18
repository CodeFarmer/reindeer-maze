(ns reindeer-maze.render
  (:require [clojure.pprint :refer [pprint]]
            [quil.applet :refer [applet]]
            [quil.core :refer [background create-font ellipse fill
                               frame-rate height rect set-state! smooth
                               state stroke text text-font triangle
                               width]]
            [reindeer-maze.navigation :refer [wall-coordinates]]
            [reindeer-maze.net :refer [my-ip]]
            [reindeer-maze.util :refer [indexed]]))

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

(defn make-quil-setup
  [game-state-atom]
  (fn []
    (smooth)
    (text-font (create-font "Courier New-Bold" 22 true))
    (frame-rate 5)
    (background 200)

    (set-state! :board game-state-atom)))

(defn quil-draw
  []
  ;; Background
  (fill 255)
  (stroke 200)
  (rect 0 0 (width) (height))

  (let [{maze :maze
         players :players
         port-number :port-number
         [goal-x goal-y] :goal-position
         :as board} (deref (state :board))
         size (min
               (int (/ (width) (count (first maze))))
               (int (/ (height) (+ (count maze) 5))))]

    ;; Treasure at the goal
    (fill 249 244 38 128)
    (quil-block goal-x goal-y size)

    ;; Walls
    (fill 37 167 25)
    (doseq [[x y] (wall-coordinates maze)]
      (quil-tree x y size))

    ;; Players
    (doseq [[_ {[x y] :position
                [r g b] :color}] players]
      (fill r g b 200)
      (stroke 0)
      (quil-dot x y size))

    ;; Legend
    (doseq [[index [_ {[r g b] :color
                       name :name}]] (indexed players)]
      (let [dot-offset-x (+ size (* (quot index 3) 300))
            dot-offset-y (* size (+ 2 (rem index 3) (count maze)))
            text-offset-x (+ size dot-offset-x)
            text-offset-y dot-offset-y]
        (fill r g b)
        (stroke 0)
        (ellipse dot-offset-x
                 dot-offset-y
                 size
                 size)
        (text name text-offset-x text-offset-y)))

    ;; Instructions
    (fill 0)
    (stroke 0)

    (text (format "rdcp://%s:%d/" (my-ip) port-number)
          size
          (* size (+ 1 (count maze))))))

(defn create-applet
  [game-state-atom screen-size]
  (applet :title "Reindeer Maze"
          :size screen-size
          :setup (make-quil-setup game-state-atom)
          :draw quil-draw))
