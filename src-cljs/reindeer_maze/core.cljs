(ns reindeer-maze.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async]
            [goog.dom :as dom]
            [goog.string]
            [goog.style :as style]
            [goog.events :as events]
            [reindeer-maze.devel :refer [log]]
            [reindeer-maze.async :refer [connect!]]))

; (log "The demo element is " (dom/getElement "demo") "and if you're seeing this, it's working.")

(defn grid-to-simple-list
  [grid]
  (mapcat (fn [y row]
            (map (fn [x cell]
                   {:x x
                    :y y
                    :cell cell})
                 (range)
                 row))
          (range)
          grid))

(def client-channel (connect! "ws://localhost:8000"))

(def maze (atom nil))

;; #_(go
;;  (while true
;;    (let [[[type value] channel] (alts! (vals client-channel))]
;;      (case type
;;        :message (do
;;                   (reset! maze value)
;;                   (log value))
;;        (log value)))))

(.log js/console maze)
(log (deref maze))
;; (log "KAJ" (count (take 1000 (grid-to-simple-list @maze))))
(* 5 6)
;; (do
;;   (doto (-> (.select js/d3 "svg#maze")
;;            (.selectAll ".maze-row")
;;            (.data (clj->js (grid-to-simple-list @maze))))
;;    (-> .enter
;;        (.append "rect")
;;        (.attr "class" "maze-row")
;;        (.attr "width" 10)
;;        (.attr "height" 0)
;;        (.attr "x" (fn [datum]
;;                     (log "DATUM" datum)
;;                     5))
;;        (.style "fill" "black")
;;        (.text str))
;;    (-> .exit
;;        .remove)
;;    )
;;   :ok)
