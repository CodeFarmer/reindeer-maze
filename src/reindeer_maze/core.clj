(ns reindeer-maze.core
  (:require [clojure.edn :as edn]
            [clojure.string :refer [join trim lower-case]]
            [maze.generate :refer [generate-maze]]
            [quil.applet :refer [defapplet]]
            [quil.core :refer [background create-font ellipse
                               fill frame-rate height rect set-state!
                               smooth state stroke text text-font
                               width]]
            [reindeer-maze.color :refer [palette]]
            [reindeer-maze.navigation :refer [path-between
                                              possible-moves
                                              random-free-position
                                              wall-coordinates]]
            [reindeer-maze.net :refer [read-from writeln my-ip]]
            [reindeer-maze.render :refer [quil-block
                                          quil-dot
                                          quil-tree]]
            [reindeer-maze.util :refer [indexed make-odd in-thread until]])
  (:import [java.net ServerSocket]))

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
                                      (writeln socket "NEW MAZE!")
                                      {socket (update-in data [:position] (fn [_]
                                                                            (random-free-position maze)))})))})))

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


(defn quil-setup []
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

(defn quil-draw
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
  (leave-maze! (first (find-player-by-name board name))))

(defn kill-all-players!
  []
  (doseq [[socket _] (:players @current-board)]
    (leave-maze! socket)))

(def help-text
  (join "\n"
        ["===="
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
         "===="]))

(defn write-help
  [socket]
  (writeln socket help-text))

(defn maze-request-handler
  "Takes a string request (which may be nil), and applies it."
  [socket request]
  (if request
    (if-let [direction (case (-> request
                                 trim
                                 lower-case)
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
                           board))))

(defn client-handler
  [socket]
  (writeln socket "Language/team name?")

  (let [sleep-time-ms 100
        name (read-from socket)]
    ;; Join maze
    (join-maze! socket name)

    (try
      (writeln socket (formatted-possible-moves-for-player socket))
      (while true
        
        ;; Handle response.
        (maze-request-handler socket (read-from socket))
        (handle-scoring)
        (writeln socket (formatted-possible-moves-for-player socket))

        ;; Sleep
        (Thread/sleep sleep-time-ms))
      (catch Exception e
        (leave-maze! socket)))))

(defn create-server
  [& {:keys [port client-handler]}]
  (in-thread
   (let [server-socket (ServerSocket. port)]
     (until (.isClosed server-socket)
            (let [socket (.accept server-socket)]
              (in-thread
               (client-handler socket))))
     server-socket)))

(defn -main
  ([] (println "USAGE: lein run <port>"))
  
  ([port-number-as-string]
     (let [port-number (edn/read-string port-number-as-string)]
       (assert (int port-number))

       (new-board! [21 31])

       (let [server (create-server :port port-number
                                   :client-handler #'client-handler)]

         (defapplet reindeer-maze
           :title "Reindeer Maze"
           :size [1000 750]
           :setup quil-setup
           :draw quil-draw
           :on-close (fn [] (.stop server)))))))
