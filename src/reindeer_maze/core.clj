(ns reindeer-maze.core
  (require [maze.generate :refer [generate-maze wall?]]
           [clojure.pprint :refer [pprint]]
           [clojure.string :as string]
           [clojure.core.async :refer [go chan <! >!]]
           [reindeer-maze.navigation :refer :all]
           [reindeer-maze.util :refer :all]
           [reindeer-maze.color :refer [palette]]
           [reindeer-maze.render :refer :all]
           [quil.core :refer :all])
  (:import [java.net ServerSocket SocketException]
           [java.io BufferedReader InputStreamReader]))

;; TODO Win Condition.
;; TODO Distance to treasure.
;; TODO Next Round.
;; TODO Help

(defn wall-coordinates
  [maze]
  (for [[y row] (indexed maze)
        [x cell] (indexed row)
        :when (not (nil? cell))]
    [x y]))

(def current-board
  "The board is the state of a single game."
  (atom {:maze []
         :players {}}))

(defn new-board!
  [size]
  (swap! current-board
         (fn [board]
           (let [center (map (fn [x] (make-odd (int (/ x 2))))
                             size)
                 maze (generate-maze size center)]
             (merge board
                    {:maze maze
                     :present-position center}))))
  ;; TODO Reset player position too!
  :ok)

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

(defn setup []
  (smooth)
  (frame-rate 5)
  (set-state! :board #'current-board)
  (text-font (create-font "Courier New-Bold" 48 true))
  (background 200))

(defn draw
  []
  ;; Background
  (fill 255)
  (stroke 200)
  (rect 0 0 (width) (height))

  (let [{maze :maze
         players :players
         [present-x present-y] :present-position
         :as board} (deref (deref (state :board)))
         size (min
               (int (/ (width) (count (first maze))))
               (int (/ (height) (+ (count maze) 5))))]

    ;; Treasure at the present
    (fill 249 244 38 128)
    (quil-block present-x present-y size)

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
      (let [text-offset-x size
            text-offset-y (* size (+ 3 index (count maze)))]
        (fill r g b)
        (stroke 0)
        (quil-dot 1 (+ 2 index (count maze)) size)
        (text name (* 2.5 size) text-offset-y)))
    
    ;; Instructions
    (fill 0)
    (stroke 0)
    (text (format "rdcp://%s:%d/" (my-ip) 80)
          size
          (* size (+ 1 (count maze))))))

(defsketch reindeer-maze
  :title "Reindeer Maze"
  :size [875 750]
  :setup setup
  :draw draw)

(def sleep-time-ms 100)

(defn writeln
  [output-stream string]
  (.write output-stream (.getBytes (str string "\n") "UTF-8")))

(defn join-maze!
  [socket name]
  (do
    (swap! current-board
           (fn [{maze :maze
                 :as board}]
             (update-in board [:players]
                        merge {socket {:name name
                                       :color (first (shuffle palette))
                                       :position (random-free-position maze)}})))
    :ok))

(defn leave-maze!
  [socket]
  (swap! current-board
         (fn [{maze :maze
               :as board}]
           (update-in board [:players]
                      dissoc socket)))
  (.close socket))

(defn kill-all-players!
  []
  (doseq [[socket _] (:players @current-board)]
    (leave-maze! socket)))

(defn write-help
  [socket]
  (writeln (.getOutputStream socket)
           (string/join "\n" [
                                "===="
                                "Help:"
                                "-----"
                                "Type N, E, S or W to move."
                                ""
                                "I will normally answer something like, 'N6 E0 S2 W0 P?',"
                                "which means I can move north for 6 squares, south for 2,"
                                "and I can't see the presents;"
                                "or 'N0 E4 S0 W0 PE', which means I can move east for 4 squares"
                                "and I can see the presents to the east."
                                ""
                                ""
                                "If I say 'WIN' or 'LOSE', this round is over, and we're"
                                "going straight on to the next."
                                "===="
                                ])))

(defn maze-request-handler
  "Takes a string request (which may be nil), and applies it."
  [socket request]
  (if request
    (if-let [direction (case (-> request
                                 string/trim
                                 string/lower-case)
                         "n" [0 -1]
                         "s" [0 1]
                         "e" [1 0]
                         "w" [-1 0]
                         (do
                           (write-help socket)
                           [0 0]))]
      (swap! current-board
             (fn [board]
               (let [current-position (get-in board [:players socket :position])
                     new-position (map + current-position direction)]
                 (if (maze.generate/wall? new-position (:maze board))
                   board
                   (update-in board [:players socket :position] (constantly new-position))
                   )))))))

(defn possible-moves-for-player
  [socket]
  (let [{maze :maze
         players :players
         present-position :present-position} @current-board
         player-position (get-in players [socket :position])] 
    (merge (possible-moves maze player-position)
           {:present-direction (path-between player-position present-position maze )})))

(defn formatted-possible-moves-for-player
  [socket]
  (let [{:keys [north south east west present-direction]} (possible-moves-for-player socket)]
    (format "N%d E%d S%d W%d P%s" north east south west
            (case present-direction
              :north "N"
              :south "S"
              :west "W"
              :east "W"
              :hit "X"
              "?"))))

(defn client-handler
  [socket]
  (let [out (.getOutputStream socket)
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))]
    (go
     (writeln out "Language/team name?")
     (let [name (.readLine in)]
       ;; Join maze
       (join-maze! socket name)

       (try
         (writeln out (formatted-possible-moves-for-player socket))
         (while true
           
           ;; Handle response.
           (maze-request-handler socket (.readLine in))
           (writeln out (formatted-possible-moves-for-player socket))

           ;; Sleep
           (Thread/sleep sleep-time-ms))
         (catch Exception e
           (leave-maze! socket)))))))

;;; start a server that runs while true.
;;; Accept, hand off to thread.
;;; Client thread reads & writes into game.
;;
;;; C opens a connection to S
;;; Server says, "Name?"
;;; C replies.
;;; S says "N 5,E 0,S 0,W 2,G ?"
;;; C replies N/E/S/W.
;;; S says "N 4,E 5,S 1,W 0,G E"

(defn create-server
  [& {:keys [port client-handler]}]
  (let [server-socket (ServerSocket. port)]
    (doto (Thread. (fn []
                     (while (not (.isClosed server-socket))
                       (let [socket (.accept server-socket)]
                         (.start
                          (Thread. (fn [] (#'client-handler socket))))))))
      .start)
    server-socket))

(defonce server
  (create-server :port 8080
                 :client-handler #'client-handler))

;; (.close server)
