(defproject crawler-master "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                [org.clojure/clojure-contrib "1.2.0"]
                [ring/ring-jetty-adapter "0.2.5"]
                [ring-json-params "0.1.0"]
                [compojure "0.4.0"]
                [org.clojure/tools.nrepl "0.2.3"]
                [com.novemberain/langohr "2.0.1"]
                [org.clojure/core.async "0.1.267.0-0d7780-alpha"]                
                [metrics-clojure "1.0.1"]
                [clj-json "0.2.0"]]
  :main crawler-master.core)
