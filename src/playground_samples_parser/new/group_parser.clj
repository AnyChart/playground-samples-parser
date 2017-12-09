(ns playground-samples-parser.new.group-parser
  (:require [playground-samples-parser.new.sample-parser :as sample-parser]
            [clojure.java.io :refer [file]]
            [clojure.string :refer [re-quote-replacement]]
            [taoensso.timbre :refer [info]]))

(defn- to-folder-path [path]
  (if (.endsWith path "/") path (str path "/")))

(defn- prettify-name [path]
  (clojure.string/replace path #"_" " "))

(defn- fix-url [path]
  (clojure.string/replace path #" " "_"))

(defn- all-files [path extensions]
  (let [path (to-folder-path path)]
    (map #(clojure.string/replace (.getAbsolutePath %)
                                  (re-quote-replacement path)
                                  "")
         (filter #(and (not (.isDirectory %))
                       (some (fn [extension]
                               (.endsWith (.getName %) (str "." extension)))
                             extensions))
                 (tree-seq (fn [f] (and (.isDirectory f) (not (.isHidden f))))
                           (fn [d] (filter #(not (.isHidden %)) (.listFiles d)))
                           (file path))))))

(defn- get-group-samples [group-path extensions]
  (let [files (concat (.listFiles (file group-path))
                      (.listFiles (file (str group-path "_samples"))))]
    (->> files
         (filter #(and (not (.isDirectory %))
                       (not (.isHidden %))
                       (some (fn [ext] (.endsWith (.getName %) (str "." ext))) extensions)))
         (map #(.getName %)))))

(defn- get-groups-from-fs [path]
  (let [path (to-folder-path path)
        fpath (file path)]
    (->> fpath
         (tree-seq (fn [f] (and (.isDirectory f) (not (.isHidden f))))
                   (fn [d] (filter #(not (.isHidden %)) (.listFiles d))))
         (filter #(and (.isDirectory %)
                       (not (= (.getName %) "_samples"))
                       (not (= fpath %))))
         (map #(clojure.string/replace (.getAbsolutePath %)
                                       (re-quote-replacement (to-folder-path path)) "")))))

;; for old playground
(defn get-config [path file-name]
  (let [path (str (to-folder-path path) file-name)]
    (when (.exists (file path))
      (read-string (slurp path)))))

(defn- folders [path]
  (map #(.getName %)
       (filter #(and (.isDirectory %)
                     (not (.isHidden %)))
               (.listFiles (file path)))))

(defn- create-group-info [path group vars]
  ;(info "creating group:" group path (load-group-config path group))
  (let [group-path (str (to-folder-path path) (to-folder-path group))
        samples (get-group-samples group-path #{"sample" "html"})]
    (merge {:index        1000
            :gallery-name (prettify-name group)
            :gallery-url  (fix-url group)}
           (get-config group-path "group.cfg")
           {:path    group
            :hidden  (or (= samples '("Coming_Soon.sample"))
                         (= group ""))
            :root    (= group "")
            :name    (prettify-name group)
            :samples (map #(sample-parser/parse (to-folder-path path) (to-folder-path group) % vars)
                          samples)})))

;; for old playground
(defn groups [path vars]
  (->> (get-groups-from-fs path)
       (filter #(not= % "api-generator"))
       (map #(create-group-info path % vars))
       (filter #(seq (:samples %)))
       (cons (create-group-info path "" vars))
       (sort-by (juxt :index :name))))

;; for docs-engine
(defn samples [path vars]
  (doall (mapcat :samples (groups path vars))))