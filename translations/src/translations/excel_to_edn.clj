(ns translations.excel-to-edn
  (:require [camel-snake-kebab.core :as cskc]
            [dk.ative.docjure.spreadsheet :as ss]
            [translations.util]))

;; converting Excel -> EDN (receiving new translations from a translator)
;; load spreadsheet from file
;; assume each worksheet name is the name of a locale
;; one column called 'message ID', one column called 'translation'
;; convert each row into a map with these keys

(defn row->value-seq
  "Helper function to convert a spreadsheet row into a sequence of cell values."
  [row]
  (map ss/read-cell (ss/cell-seq row)))

(defn rows->maps
  "Converts the `rows`, which is a seq of seqs, into maps.
  Uses the first row as the keys for each map.
  Uses each subsequent row as one map's values."
  [rows]
  (let [header (->> rows
                    first
                    (map cskc/->kebab-case-keyword))]
    (map #(zipmap header %) (rest rows))))

(defn sheet->maps
  "Converts the `sheet` into a seq of maps.
  Uses the first row as the keys for each map.
  Uses each subsequent row as one map's values."
  [sheet]
  (let [rows (->> sheet
                  ss/row-seq
                  (map row->value-seq))]
    (rows->maps rows)))

(defn deserialize-keyword
  "If `s` matches a regular expression for a keyword,
    then converts `s` into a keyword.
    Otherwise provides `s` unchanged."
  [s]
  (if-let [found (when (some? s)
                   (re-find #"^:[^:]*$" s))]
    (clojure.edn/read-string found)
    s))

(defn convert-excel-to-edn
  [input-excel-file output-dir]
  (let [inbound-workbook (ss/load-workbook-from-file input-excel-file)
        ;; Convert into a single map in which each key is a locale
        ;; and each value is a map of message IDs to translations
        ;; for that locale.
        locales-map      (->> inbound-workbook
                              ss/sheet-seq
                              (map (juxt (comp keyword
                                               ss/sheet-name)
                                         (comp (partial into {})
                                               (partial map (fn [kvp]
                                                              (update kvp 0 deserialize-keyword)))
                                               (partial map (juxt :message-id :translation))
                                               sheet->maps)))
                              (into {}))
        dir-obj          (clojure.java.io/file output-dir)]
    (when-not (.exists dir-obj)
      (.mkdir dir-obj))
    (doseq [[locale-name translations-map] locales-map]
      (spit (str output-dir
                 "/"
                 (translations.util/coerce-locale-name locale-name)
                 ".edn")
            (with-out-str (clojure.pprint/pprint translations-map))))))
