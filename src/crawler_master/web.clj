; Attempt to port Raptor to Clojure
; (C) Johan Astborg, 2013

(ns crawler-master.web
  (:use compojure.core)
  (:use ring.middleware.json-params)
  (:use ring.middleware.static)
  (:use crawler-master.gen)
  (:use ring.middleware.params)
  (:use ring.middleware.file)
  (:require [clj-json.core :as json])
  (:require [clojure.core.async :as async :refer [<! >! <!! >!! timeout chan alt! go close!]])

  (:require [langohr.core      :as rmq]
            [langohr.channel   :as lch]
            [langohr.queue     :as lq]
            [langohr.exchange  :as le]
            [langohr.consumers :as lc]
            [langohr.basic     :as lb])

  (:use [metrics.meters])
  (:require [clojure.tools.nrepl :as repl]))

;;;;;;;;;MQ

(def counter (ref 0))

(def ^{:const true}
  crawler-exchange "url-crawler")

(def conn (rmq/connect {:host "ec2-54-213-238-4.us-west-2.compute.amazonaws.com"}))
(def ch (lch/open conn))
(le/declare ch crawler-exchange "topic" :durable false :auto-delete true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;(defmeter urls-meter "urls")

(def urls-meter (meter "urls-meter" "urls"))

;; hash map with urls
(def crawled-urls (java.util.concurrent.ConcurrentHashMap.))

(defn new-urls [urls]
  (let [ul (clojure.string/split urls #"\s")]
    (doseq [url ul]
      (try
        (.putIfAbsent crawled-urls url "")
        (dosync (alter counter inc))
        (mark! urls-meter)
        (catch Exception e (println (str "caught exception: " (.getMessage e)))))))
  (println "# URLS: " (.size crawled-urls) " -- " (rate-one urls-meter)))

(defn handle-urls [urls]
  (new-urls urls))

(defn create-topic-channel-reader
  [ch ach queue-name routing-key]
  (let [queue-name' (.getQueue (lq/declare ch queue-name :exclusive false :auto-delete true))
        handler     (fn [ch {:keys [routing-key] :as meta} ^bytes payload]
                      (go (>! ach (String. payload "UTF-8")))
                      ;(println (format "[consumer] Consumed '%s' from %s, routing key: %s" (String. payload "UTF-8") queue-name' routing-key))
                      )]
    (lq/bind    ch queue-name' crawler-exchange :routing-key routing-key)
    (lc/subscribe ch queue-name' handler :auto-ack true) ach))

;; reads a channel
(defn ach-mq-reader [ch]
  "a channel reader that will close on channel close"
  (async/go-loop []
                 (when-let [in (<! ch)]
                   ;(println "*** NEW: " in)
                   (handle-urls in)
                   (recur))
                 (println "Closing..." (.toString (java.util.Date.)))))

(defn publish-quote [ch q key]
  (let [payload (str q)]
  (lb/publish ch crawler-exchange key payload :content-type "text/plain" :type "quote.update")))

(let [ach (chan)]
  (create-topic-channel-reader ch ach crawler-exchange "discovered-urls")
  (ach-mq-reader ach))

(defn load-urls [file]
  (with-open [rdr (clojure.java.io/reader file)]
    (doseq [url (line-seq rdr)]
      (.putIfAbsent crawled-urls url ""))))

(defn apa []
  (mark! urls-meter)
  (println "APA")
  ;; load urls from file
  (load-urls "/tmp/urls.txt")
  (async/thread (doseq [url (iterator-seq (.keys crawled-urls))]
        (do (Thread/sleep 25)
      (publish-quote ch url "to-crawl-urls"))))
  (publish-quote ch "http://www.appguiden.se" "to-crawl-urls")
  (publish-quote ch "http://www.cse.psu.edu/~groenvel/urls.html" "to-crawl-urls"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

; UUID generator
(defn uuid [] (str (java.util.UUID/randomUUID)))

; pages
(defn indexpage [] (str
    (html
      (head
        (title "HTML-gen using Clojure")
      )
    (body
      (h1 "Welcome to Crawler - Master"))
      (ahref "/elems" "My page")
      (h2 "Status")
      (p "Nr of URLs: " (.size crawled-urls))
      (p (str "Crawling rate (new urls / min): " (rate-one urls-meter)))
      (p (.toString conn))
      (footer
        (hr)
        (small "Crawler v. 0.01")
      )
    )
  )
)

(defroutes handler

  (GET "/johan" []
    (json-response {"hello" "johan"}))

  (GET "/html" []
    (indexpage)))


(def app
  (-> (wrap-params handler)))
