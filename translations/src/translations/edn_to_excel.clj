(ns translations.edn-to-excel
  (:require [dk.ative.docjure.spreadsheet :as ss]
            [translations.util]))

;; converting EDN -> Excel (for handing over to a translator)
;; grab all files in a given directory
;; filter to files with the EDN extension

(defn split-file-name
  "Splits a file name on each occurance of a dot."
  [file-name]
  (clojure.string/split file-name #"\."))

(defn get-file-type
  [file]
  (-> file
      .getName
      split-file-name
      last
      .toLowerCase))

(defn get-file-name
  [file]
  (->> file
       .getName
       split-file-name
       first))

(defn maps->table
  [ms]
  (let [header (->> ms
                    (map keys)
                    (mapcat identity)
                    distinct)]
    (cons header
          (map vals ms))))

(defn- coerce-translation
  [t]
  (cond
    (keyword? t) (pr-str t)
    (string? t)  t
    :else        (str t)))

;; convert into a seq of maps with locale-name, translations
(defn- transform-for-docjure [locale]
  ((juxt :locale-name :translations)
   (-> locale
       (update :locale-name translations.util/coerce-locale-name)
       (update :translations
               (fn [translations]
                 (maps->table
                  (map (fn [[message-id translation]]
                         {"message ID"  (coerce-translation message-id)
                          "translation" (coerce-translation translation)})
                       translations)))))))

(defn convert-edn-to-excel
  [input-dir output-excel-file]
  (let [locale-files      (some->> input-dir
                                   clojure.java.io/file
                                   file-seq
                                   (filter (fn [file]
                                             (= "edn"
                                                (get-file-type file)))))
        locales           (->> locale-files
                               (map (fn [file]
                                      (let [locale-name  (keyword (get-file-name file))
                                            translations (->> file
                                                              slurp
                                                              clojure.edn/read-string)]
                                        {:locale-name  locale-name
                                         :translations translations}))))
        docjure-args      (mapcat identity (map transform-for-docjure locales))
        ;; create a new workbook
        ;; each locale name becomes a sheet name
        ;; each translation becomes a row
        ;; (maps -> table)
        outbound-workbook (apply ss/create-workbook docjure-args)]
    (ss/save-workbook! output-excel-file outbound-workbook)))
