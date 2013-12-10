(ns reindeer-maze.webserver
  (:require [clojure.pprint :refer [pprint]]
            [clojure.core.async :refer [chan go >! <!]]
            [ring.util.response :as response]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :refer [response]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.core :refer :all]
            [maze.generate :refer [generate-maze]]
            [com.keminglabs.jetty7-websockets-async.core :refer [configurator]]))

(defn log
  [& args]
  (pprint args)
  (flush))

(defn wrap-logging
  [handler]
  (fn [request]
    (let [response (handler request)]
      (log "Handled" {:request  (select-keys request [:uri :remote-addr :query-params :server-name])
                      :repsonse (select-keys response [:status :content-type])})
      response)))

(defroutes app-routes
  (GET "/" [] (response/redirect "/index.html"))

  (route/resources "/"))

(def app (-> #'app-routes
             wrap-logging
             handler/site))

(def server-channel (chan))

(def ws-configurator
  (configurator server-channel
                {:path "/"}))

(defonce server
  (jetty/run-jetty #'app
                   {:configurator ws-configurator
                    :port 8000
                    :join? false}))

(go (loop []
      (let [ws-req (<! server-channel)]
        (>! (:in ws-req) (pr-str
                          (generate-maze [81 51])
                          #_[:thing (java.util.Date.) "Hello new websocket client!"]
                          ))
        (recur))))

