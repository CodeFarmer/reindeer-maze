(ns reindeer-maze.core
  (require [maze.generate :refer [generate-maze wall?]]
           [clojure.pprint :refer [pprint]]
           [clojure.edn :as edn]
           [clojure.string :as string]
           [clojure.core.async :refer [go chan <! >!]]
           [reindeer-maze.navigation :refer :all]
           [reindeer-maze.util :refer :all]
           [reindeer-maze.color :refer [palette]]
           [reindeer-maze.render :refer :all]
           [quil.core :refer :all])
  (:import [java.net ServerSocket SocketException]
           [java.io BufferedReader InputStreamReader]))

(defn writeln
  [output-stream string]
  (.write output-stream (.getBytes (str string "\n") "UTF-8")))

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

(defn scramble-player-positions
  [board]
  (let [{maze :maze
         players :players} board]
    (merge board
           {:players (apply merge (for [[socket data] players]
                                    (do
                                      (writeln (.getOutputStream socket) "NEW MAZE!")
                                      {socket (update-in data [:position] (fn [_]
                                                                            (random-free-position maze)
                                                                            ))})))})))

(defn new-board!
  [size]
  (swap! current-board
         (fn [board]
           (let [center (map (fn [x] (make-odd (int (/ x 2))))
                             size)
                 maze (generate-maze size center)]
             (-> board
                 (merge {:maze maze
                         :present-position center})
                 scramble-player-positions))))
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
  (text-font (create-font "Courier New-Bold" 22 true))
  (frame-rate 5)
  (set-state! :board #'current-board)
  (background 200))

(defn apply-points
  [player-data points]
  (merge-with +
              player-data
              {:points points}))

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
                       points :points
                       name :name
                       :or {points 0}}]] (indexed players)]
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
        (text (format "%d %s"
                      points
                      name) text-offset-x text-offset-y)))
    
    ;; Instructions
    (fill 0)
    (stroke 0)
    (text (format "rdcp://%s:%d/" (my-ip) 8080)
          size
          (* size (+ 1 (count maze))))))

(def sleep-time-ms 100)

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

(defn find-player-by-name
  [board the-name]
  (first (filter (fn [[socket {name :name}]]
             (= name the-name))
           (:players board))))

(defn leave-maze!
  [socket]
  (swap! current-board
         (fn [{maze :maze
               :as board}]
           (update-in board [:players]
                      dissoc socket)))
  (.close socket))

(defn kill-player-by-name!
  [board name]
  (leave-maze! (first(find-player-by-name board name))))

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

(defn winner?
  [{players :players
    present-position :present-position
    :as board}]
  (filter (fn [[socket {position :position}]]
            (= position present-position))
          players))

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
              :east "E"
              :hit "X"
              "?"))))

(defn apply-scoring
  [{present-position :present-position
    :as board}]
  (update-in board [:players]
             (fn [players]
               (apply merge (for [[socket {position :position
                                           :as data} :as player] players]
                              {socket (if (not= position present-position)
                                        data
                                        (merge-with + data {:points 10})
                                        )})))))

(defn handle-scoring
  []
  
  (swap! current-board (fn [board]
                         (when-let [winners (winner? board)]
                           (println "WINNER!")
                           board
                           )))
  )

(defn client-handler
  [socket]
  (let [out (.getOutputStream socket)
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))]
    (go
     (writeln out "Language/team name?")
     (let [name (.readLine in)]
       ;; Join maze
       #_(when (= name "java rules ok")
         (writeln out "Fuck off!\n")

         (throw (Exception. "Fuck off!"))
         )
       (join-maze! socket name)

       (try
         (writeln out (formatted-possible-moves-for-player socket))
         (while true
           
           ;; Handle response.
           (maze-request-handler socket (.readLine in))
           (handle-scoring)
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

(defn -main
  ([] (println "USAGE: lein run <port>"))
  
  ([port-number-as-string]
     (let [port-number (edn/read-string port-number-as-string)]
       (assert (int port-number))

       (new-board! [21 31])

       (create-server :port port-number
                      :client-handler #'client-handler)

       (defsketch reindeer-maze
         :title "Reindeer Maze"
         :size [1000 750]
         :setup setup
         :draw draw))))
