(ns reindeer-maze.devel
 (:require [clojure.browser.repl :as repl]
           [goog.net.xpc.CfgFields :as cfg-fields]))

(defn log
 ([object]
   (.log js/console (clj->js object)))
 ([object & objects]
    (log (cons object objects))))

(set! *print-fn* log)
;
(defn connect-to-repl
 []
 (aset goog.net.xpc.CfgFields "PEER_POLL_URI" "http://localhost:9000/")
 (repl/connect "http://localhost:9000/repl"))

(connect-to-repl)
