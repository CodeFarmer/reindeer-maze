(ns reindeer-maze.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async]
            [goog.dom :as dom]
            [goog.string]
            [goog.style :as style]
            [goog.events :as events]
            [reindeer-maze.devel :refer [log]]
            [reindeer-maze.async :refer [connect!]]))

(log "The demo element is " (dom/getElement "demo") "and if you're seeing this, it's working.")

(def client-channel (connect! "ws://localhost:8000"))

(go
 (while true
   (let [[[type value] channel] (alts! (vals client-channel))]
     (case type
       :message (log value)
       (log value)))))
