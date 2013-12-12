(ns reindeer-maze.net
  (:import [java.io OutputStream]
           [java.net Socket]))

(defprotocol Writable
  (write [this str]))

(extend-protocol Writable
  OutputStream
  (write [this str] (.write this (.getBytes str "UTF-8")))
  Socket
  (write [this str] (write (.getOutputStream this) str)))
