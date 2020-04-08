(ns ml-conversion.core
  (:require [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dk.ative.docjure.spreadsheet :as ss]))

(defn make-manifest-path
  [surrogate-dir]
  (-> surrogate-dir
      io/file
      (io/file "_manifest.json")
      .getCanonicalPath))

(defn transpose
  "Transposes the 2D matrix `A`,
  for a matrix represented as a vector of vectors."
  [A]
  (apply mapv (fn [& args] (vec args)) A))

(defn maps->table
  [ms]
  (let [header (->> ms
                    (map keys)
                    (mapcat identity)
                    distinct
                    sort
                    vec)]
    (vec (cons header
               (map (fn [m]
                      (mapv (fn [k]
                              (get m k))
                            header))
                    ms)))))

(defn- humanize
  [named]
  (-> named
      (name)
      (string/replace "-" " ")
      (string/capitalize)))

(defn manifest->table
  [manifest]
  (let [limited          (select-keys manifest
                                      [:name :description
                                       :base-idf :climate-file])
        table            (maps->table [limited])
        humanized-header (update table
                                 0
                                 (fn [row]
                                   (mapv humanize
                                         row)))]
    (transpose humanized-header)))

(defn features->table
  [features]
  (let [table   (->> features
                     (map #(select-keys %
                                        [:kind
                                         :ui/control
                                         :units
                                         :notes
                                         :ui/position
                                         :tf/training-min
                                         :tf/training-max
                                         :formula
                                         :default
                                         :energy-plus-label
                                         :feature/id]))
                     (map #(update %
                                   :feature/id
                                   humanize))
                     (map #(update %
                                   :kind
                                   humanize))
                     maps->table)
        cleaned (-> table
                    (update
                     0
                     (fn [row]
                       (replace {:kind              :type
                                 :ui/control        :kind
                                 :ui/position       :sub_index
                                 :tf/training-min   :min
                                 :tf/training-max   :max
                                 :energy-plus-label :label
                                 :formula           :pyfunction
                                 :feature/id        :descriptive-name}
                                row)))
                    (update
                     0
                     (fn [row]
                       (mapv humanize
                             row))))]
    cleaned))

(defn edn->xlsx
  "Converts the `manifest` and `features` from EDN
  to an in-memory Excel workbook."
  [manifest features]
  (let [manifest-table (manifest->table manifest)
        features-table (features->table features)
        table          (mapcat identity
                               [[["NZN METADATA"]]
                                [[]]
                                manifest-table
                                [[]
                                 []]
                                features-table])
        worksheets-map {"Metadata" table}
        workbook       (apply ss/create-workbook
                              (mapcat identity
                                      worksheets-map))]
    workbook))

(defn implode-keywords
  "Reverses the explode-keywords trick of encoding namespaced keywords.

   `{:ui {:pos 1 :category 'foo}} => {:ui/pos 1 :ui/category 'foo}`"
  [m]
  (reduce (fn [m' [k v]]
            (if (map? v)
              (let [submap (into {} (map (fn [[k' v']] [(keyword (name k) (name k')) v']) v))]
                (merge m' submap))
              (assoc m' k v)))
          {} m))

(defn- post-process-manifest
  [manifest]
  (update manifest
          :tf-models
          (fn [tf-models]
            (reduce
             (fn [models' model]
               (conj models'
                     (-> model
                         (update :features (fn [fts]
                                             (map implode-keywords fts)))
                         (update :features (fn [fts]
                                             (map (fn [ft]
                                                    (update ft :feature/id #(as-> % $ (csk/->kebab-case $) (string/upper-case $) (symbol $)))) fts))))))
             [] tf-models))))

(defn json->xlsx!
  "Accepts two filesystem paths,
  1. the directory where the surrogate's manifest
     is stored, and
  2. the file name where the resulting Excel workbook
     will be saved.
  Converts the given manifest file from JSON
  into an Excel workbook with one worksheet in it."
  [{:keys [surrogate-dir workbook-path]}]
  (let [manifest (-> (make-manifest-path surrogate-dir)
                     slurp
                     (json/read-str :key-fn keyword)
                     post-process-manifest)
        features (mapcat :features (:tf-models manifest))
        workbook (edn->xlsx manifest features)]
    (ss/save-workbook! workbook-path workbook)))
