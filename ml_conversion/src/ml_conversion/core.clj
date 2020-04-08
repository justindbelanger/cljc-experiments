(ns ml-conversion.core
  (:require [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dk.ative.docjure.spreadsheet :as ss]))

;; * File-system utils

(defn make-manifest-path
  [surrogate-dir]
  (-> surrogate-dir
      io/file
      (io/file "_manifest.json")
      .getCanonicalPath))

;; * Working with tabular data (kind of like Pandas' DataFrames)

(defn transpose
  "Transposes the 2D matrix `A`,
  for a matrix represented as a vector of vectors."
  [A]
  (apply mapv (fn [& args] (vec args)) A))

(defn select-series
  "Provides a DataFrame
  containing only the Series from the `data-frame`
  whose labels are in the `series-labels`,
  in the order given.

  This also allows for selecting multiple copies of the same Series.

  Note: requesting a label that doesn't exist in the `data-frame`
  is not allowed and will result in an error.

  Similar to `df[[\"col1\", \"col2\"]]` in Pandas."
  [series-labels data-frame]
  (let [series        (transpose data-frame)
        label->series (->>  series
                            (map (juxt first identity))
                            (into {}))]
    (->> series-labels
         (map (fn [label]
                (if (contains? label->series label)
                  (get label->series label)
                  (throw (ex-info "Invalid label" {:label label})))))
         transpose)))

(defn sort-index-series
  "Provides a DataFrame with the Series sorted
  by comparing their labels with `cmp-fn`.

  `cmp-fn` defaults to `compare`.

  Similar to `df.sort_index(axis=1)` in Pandas."
  ([cmp-fn data-frame]
   (->> data-frame
        transpose
        (sort #(cmp-fn (first %1) (first %2)))
        transpose))
  ([data-frame]
   (sort-index-series compare data-frame)))

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

;; * Converting ML manifest and features into tables

(defn manifest->table
  [manifest]
  (let [ordered-column-names
        [:name :description
         :base-idf :climate-file]
        limited          (select-keys manifest
                                      ordered-column-names)
        table            (maps->table [limited])
        humanized-header (as-> table $
                           (select-series ordered-column-names
                                          $)
                           (update $ 0 (fn [row]
                                         (mapv humanize
                                               row))))]
    (transpose humanized-header)))

(defn features->table
  [features]
  (let [ordered-column-names
        [:type :sub_index :descriptive-name
         :label :kind :units :pyfunction
         :min :max :default :notes]
        table   (->> features
                     (map #(update %
                                   :kind
                                   humanize))
                     (map #(update %
                                   :feature/id
                                   humanize))
                     (map #(clojure.set/rename-keys
                            %
                            {:kind              :type
                             :ui/control        :kind
                             :ui/position       :sub_index
                             :tf/training-min   :min
                             :tf/training-max   :max
                             :energy-plus-label :label
                             :formula           :pyfunction
                             :feature/id        :descriptive-name}))
                     (map #(select-keys %
                                        ordered-column-names))
                     maps->table)
        cleaned (as-> table $
                  (select-series ordered-column-names
                                 $)
                  (update $ 0 (fn [row]
                                (mapv humanize row))))]
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

;; * Converting manifest and features as JSON into EDN

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

;; * Entry point

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
