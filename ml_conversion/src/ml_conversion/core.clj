(ns ml-conversion.core
  (:require [camel-snake-kebab.core :as csk]
            [clojure.data.json :as json]
            [dk.ative.docjure.spreadsheet :as ss]))

;; read files in
;; convert to EDN
;; use same transformations as in NZN
;; need to map manifest and features to cells, columns, rows.

(defn manifest-path
  [surrogate-dir]
  (str surrogate-dir "/" "_manifest.json"))

(defn features-paths
  [manifest surrogate-dir]
  (->> manifest
       :tf/models
       (map :path)
       (map (fn [path]
              (let [tfjs-model-dir (-> (str surrogate-dir "/" path)
                                       clojure.java.io/file
                                       .getAbsoluteFile
                                       .getParent)]
                (str tfjs-model-dir "/" "_features.json"))))))

#_(defn append
  "Adds the given `rows` to the given `table`"
  )

(defn transpose
  "Transposes the 2D matrix `A`,
  for a matrix represented as a vector of vectors."
  [A]
  (apply mapv (fn [& args] (vec args)) A))

(defn maps->table
  [ms]
  (let [header (-> ms
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
      (clojure.string/replace "-" " ")
      (clojure.string/capitalize)))

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
  (let [table            (maps->table (map #(update %
                                                    :kind
                                                    humanize)
                                           (map #(update %
                                                         :feature/id
                                                         humanize)
                                                (map #(select-keys %
                                                                   [:kind
                                                                    :ui/control
                                                                    :notes
                                                                    :ui/position
                                                                    :tf/training-min
                                                                    :tf/training-max
                                                                    :energy-plus-label
                                                                    :feature/id])))))
        renamed-header   (update table
                                 0
                                 (fn [row]
                                   (replace {:kind              :type
                                             :ui/control        :kind
                                             :ui/position       :sub_index
                                             :tf/training-min   :min
                                             :tf/training-max   :max
                                             :energy-plus-label :label
                                             :feature/id        :descriptive-name}
                                            row)))
        humanized-header (update table
                                 0
                                 (fn [row]
                                   (mapv humanize
                                         row)))]
    humanized-header))

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
        workbook       (ss/create-workbook {"metadata" table})]
    workbook))

(defn implode-keywords
  [f]
  nil)

(defn- post-process-manifest
  [manifest]
  nil)

#_(defn xlsx->edn
  "Converts an in-memory Excel workbook to EDN files
  representing a manifest and features.
  Note: this doesn't include the TensorFlow model topology and weights."
  []
  nil)

(defn json->xlsx!
  "Reads two positional args from standard input,
  1. the directory where the surrogate's manifest
     and features are stored, and
  2. the file name where the resulting Excel workbook
     will be saved.
  Converts the given manifest and features files from JSON
  into an Excel workbook with one worksheet in it."
  []
  (let [args          (read-line)
        surrogate-dir (first args)
        workbook-dir  (second args)
        manifest      (post-process-manifest
                       (json/read-str (manifest-path surrogate-dir)))
        features      (mapcat :features (:tf-models manifest))
        #_ (map (comp post-process-features
                      json/read-str)
                (features-paths manifest
                                surrogate-dir))
        workbook      (json->xlsx manifest features)]
    (ss/save-workbook! workbook-dir workbook)))

#_(defn xlsx->json!
  []
  nil)
