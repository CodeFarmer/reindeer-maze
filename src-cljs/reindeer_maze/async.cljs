(ns reindeer-maze.async
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! chan >! <!]]
            [cljs.reader :refer [read-string]]
            [goog.net.WebSocket]
            [goog.net.WebSocket.MessageEvent]
            [goog.net.WebSocket.EventType :as Events]))

(defn connect!
  [url]
  (let [ws  (goog.net.WebSocket.)
        in  (chan)
        out (chan)]
    (goog.events.listen ws Events/OPENED  (fn [e] (put! out [:opened e])))
    (goog.events.listen ws Events/CLOSED  (fn [e] (put! out [:closed e])))
    (goog.events.listen ws Events/MESSAGE (fn [e] (put! out [:message (read-string (.-message e))])))
    (goog.events.listen ws Events/ERROR   (fn [e] (put! out [:error e])))
    (.open ws url)
    (go (loop [msg (<! in)]
          (when msg
            (.send ws msg)
            (recur (<! in)))))
    {:in in
     :out out}))
