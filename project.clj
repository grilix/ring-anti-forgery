(defproject org.clojars.grilix/ring-anti-forgery "1.0.2"
  :description "Ring middleware to prevent CSRF attacks"
  :url "https://github.com/grilix/ring-anti-forgery"
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [crypto-random "1.2.0"]
                 [crypto-equality "1.0.0"]]
  :plugins [[codox "0.8.5"]]
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.5"]]}
   :1.4 {:dependencies [[org.clojure/clojure "1.4.0"]]}
   :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}})
