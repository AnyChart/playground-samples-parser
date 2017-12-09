(defproject com.anychart/playground-samples-parser "0.2.0"
  :description "Anychart samples parsing library"
  :url "https://github.com/AnyChart/playground-samples-parser"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; html
                 [org.jsoup/jsoup "1.11.2"]
                 [enlive "1.1.6"]
                 [toml "0.1.2"]
                 ;; logging
                 [com.taoensso/timbre "4.3.1"]])
