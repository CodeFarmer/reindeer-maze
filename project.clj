(defproject reindeer-maze "0.1.0"
  :dependencies [
                 ;; Clojure
                 [org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]

                 [maze "0.2.0"]
                 [quil "1.6.0"]

                 ;; Web
                 [enlive "1.1.5"]
                 [compojure "1.1.6"]
                 [clj-oauth2 "0.2.0" :exclusions [org.clojure/data.json commons-codec]]
                 [clj-http "0.7.8" :exclusions [org.clojure/tools.reader]]
                 [cheshire "5.2.0"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-core "1.2.1" :exclusions [org.clojure/tools.reader]]
                 [ring/ring-json "0.2.0"]
                 [fogus/ring-edn "0.2.0"]
                 [com.keminglabs/jetty7-websockets-async "0.1.0"]

                 ;; Database
                 [org.clojure/java.jdbc "0.3.0-beta1"]
                 [org.postgresql/postgresql "9.2-1003-jdbc4"]
                 [yesql "0.2.2"]

                 ;; ClojureScript
                 [org.clojure/clojurescript "0.0-2080"]
                 [enfocus "2.0.2"]]
  :profiles {:dev {:dependencies [[ring-mock "0.1.5"]]}
             :prod {:ring {:auto-reload? false}}}
  :resources-path "resources"
  :plugins [[lein-ring "0.8.7" :exclusions [org.clojure/clojure]]
            [lein-cljsbuild "1.0.0-alpha2"]]
  :aliases {"cljs-repl" ["trampoline" "cljsbuild" "repl-listen"]
            "cljs" ["trampoline" "cljsbuild" "auto"]
            "web" ["ring" "server-headless"]}
  :cljsbuild {:builds [{:id "mobile"
                        :source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/cljs/index.js"
                                   :output-dir "resources/public/cljs"
                                   :externs ["resources/public/js/lib/d3.v3.js"]
                                   :optimizations :none
                                   :source-map true}}]})
