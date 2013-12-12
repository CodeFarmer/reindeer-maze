(ns reindeer-maze.net
  (:import [java.io BufferedReader InputStreamReader OutputStream]
           [java.net Socket InetAddress]))

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
