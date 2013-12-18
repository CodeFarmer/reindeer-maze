(ns reindeer-maze.net
  (:require [reindeer-maze.util :refer [in-thread until]])
  (:import (java.io BufferedReader InputStreamReader OutputStream)
           (java.net InetAddress ServerSocket Socket SocketException)))

(defprotocol IWritable
  (write-to [this str]))

(extend-protocol IWritable
  OutputStream
  (write-to [this str] (.write this (.getBytes str "UTF-8")))
  Socket
  (write-to [this str] (write-to (.getOutputStream this) str)))

(defn writeln
  [destination string]
  (write-to destination (str string "\n")))

(defprotocol IReadable
  (read-from [this]))

(extend-protocol IReadable
  Socket
  (read-from [this]
    (-> this
        .getInputStream
        InputStreamReader.
        BufferedReader.
        .readLine)))

(defn my-ip
  "Return the IP address of the current process."
  []
  (-> (InetAddress/getLocalHost)
      .getHostAddress))

(defn create-network-server
  [game-state-atom & {:keys [port client-handler]}]
  (let [server-socket (ServerSocket. port)]
    (in-thread
     (try
       (until (.isClosed server-socket)
         (let [socket (.accept server-socket)]
           (in-thread
            (client-handler game-state-atom socket))))
       (catch SocketException e (println e))))
    server-socket))
