(ns crawler-master.core
  (:use ring.adapter.jetty)
  (:require [crawler-master.web :as web]))

(def server-port 8080)

;(defn start-nrepl-server []
;  (defonce server (start-server :port nrepl-port)))

(defn -main [& args]
;  (start-nrepl-server)
  (println "## Crawler - Master" args)
  (web/apa)
  (run-jetty #'web/app {:port server-port}))
