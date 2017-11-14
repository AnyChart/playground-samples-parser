(ns playground-samples-parser.new.sample-parser
  (:require [net.cgrand.enlive-html :as html]
            [taoensso.timbre :refer [info error]]
            [clojure.string :as s :refer (trim-newline)]
            [clojure.java.io :refer [file]]
            [toml.core :as toml]
            [clojure.string :as string]))


(defn- ^String trim-newline-left [^CharSequence s]
  (loop [index 0]
    (if (= 0 (.length s))
      ""
      (let [ch (.charAt s index)]
        (if (or (= ch \newline) (= ch \return))
          (recur (inc index))
          (.. s (subSequence index (.length s)) toString))))))

(defn trim-trailing [s]
  (clojure.string/replace s #"\s*$" ""))

(defn space-count [s]
  (loop [index 0]
    (if (= (.charAt s index) \space)
      (recur (inc index))
      index)))

(defn trim-code
  "Delete so many spaces from string start as it have at first line"
  [s]
  (when s
    (let [trailing-s (-> s trim-trailing trim-newline-left)
          space-count (space-count trailing-s)
          pattern (re-pattern (str "(?m)^[ ]{" space-count "}"))]
      (clojure.string/replace trailing-s pattern ""))))


(defn parse-html-sample [path s]
  (let [page (html/html-snippet s)
        scripts (->> (html/select page [:script])
                     (filter #(some? (:src (:attrs %))))
                     (map #(:src (:attrs %))))
        local-scripts (->> (html/select page [:script])
                           (filter #(nil? (:data-export (:attrs %))))
                           (map #(:src (:attrs %))))

        code (some->> (html/select page [:script])
                      (filter #(not (:src (:attrs %))))
                      first
                      :content
                      (apply str))

        markup (some->> (html/select page [:div])
                        first
                        html/emit*
                        (apply str))

        style (some->> (html/select page [:style])
                       first
                       :content
                       (apply str))

        css-libs (->> (html/select page [:link])
                      (filter #(and (= (-> % :attrs :rel) "stylesheet")
                                    (-> % :attrs :href some?)))
                      (map #(-> % :attrs :href)))

        name (some->> (html/select page [:meta])
                      (filter #(= "ac:name" (:name (:attrs %))))
                      first :attrs :content)

        exports (some->> (html/select page [:meta])
                         (filter #(= "ac:export" (:name (:attrs %))))
                         first :attrs :content)

        description (some->> (html/select page [:meta])
                             (filter #(= "ac:desc" (:name (:attrs %))))
                             first :attrs :content)

        short-description (some->> (html/select page [:meta])
                                   (filter #(= "ac:short-desc" (:name (:attrs %))))
                                   first :attrs :content)

        tags-content (->> (html/select page [:meta])
                          (filter #(= "ac:tags" (:name (:attrs %))))
                          first :attrs :content)
        tags (if (and tags-content
                      (seq tags-content))
               (clojure.string/split tags-content #"\s*,\s*")
               [])
        all-tags tags]
    {:name              name
     :description       description
     :short-description short-description

     :tags              all-tags
     :deleted-tags      []
     :exports           exports

     :scripts           scripts
     :local-scripts     local-scripts
     :styles            css-libs

     :code-type         "js"
     :code              (or (trim-code code) "")

     :markup-type       "html"
     :markup            (or (trim-code markup) "")

     :style-type        "css"
     :style             (or (trim-code style) "")}))



(defn parse-toml-sample [path s]
  (try
    (let [data (toml/read s :keywordize)]
      {:name              (-> data :name)
       :description       (-> data :description)
       :short-description (-> data :short-description)

       :tags              (-> data :meta :tags)
       :exports           (-> data :meta :export)

       :scripts           (-> data :deps :scripts)
       :local-scripts     (-> data :deps :local-scripts)
       :styles            (-> data :deps :styles)

       :code-type         (-> data :code :type)
       :code              (-> data :code :code trim-code)

       :markup-type       (-> data :markup :type)
       :markup            (-> data :markup :code trim-code)

       :style-type        (-> data :style :type)
       :style             (-> data :style :code trim-code)})
    (catch Exception e
      (info "parse TOML error: " path e)
      nil)))

(defn- sample-path [base-path group sample]
  (if (.exists (file (str base-path group sample)))
    (str base-path group sample)
    (str base-path group "_samples/" sample)))

(defn replace-vars [s vars]
  (reduce (fn [s [key value]]
            (clojure.string/replace s
                                    (re-pattern (str "\\{\\{" (name key) "\\}\\}"))
                                    value))
          s vars))

(defn html? [s]
  (.startsWith (.toLowerCase s) "<!doctype html"))

(defn parse [base-path group sample vars]
  ;(prn "parse sample: " base-path group config sample)
  (let [path (sample-path base-path group sample)
        name (clojure.string/replace sample #"\.(html|sample)$" "")
        sample-str (-> path slurp (replace-vars vars))
        base-info (cond (.endsWith path ".html") (parse-html-sample path sample-str)
                        (.endsWith path ".sample") (if (html? sample-str)
                                                     (parse-html-sample path sample-str)
                                                     (parse-toml-sample path sample-str)))]
    (when base-info
      (assoc base-info
        :name (or (:name base-info) (clojure.string/replace name #"_" " "))
        :hidden (= name "Coming_Soon")
        :url (str (.substring group 1)
                  (clojure.string/replace name #"%" "%25"))))))