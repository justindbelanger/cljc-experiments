(ns translations.core)

(comment
  (require '[dk.ative.docjure.spreadsheet :as ss])
  (require '[camel-snake-kebab.core :as cskc])
  (require '[clojure.edn])

  (defn coerce-locale-name
    [locale-name]
    (cond
      (keyword? locale-name) (name locale-name)
      (string? locale-name)  locale-name
      :else                  (throw (ex-info "Invalid locale name"
                                             {:locale-name locale-name}))))

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
    (if-let [found (re-find #"^:[^:]*$" s)]
      (clojure.edn/read-string found)
      s))
  (def file "tmp/translations.xlsx")
  (def inbound-workbook (ss/load-workbook-from-file file))
  ;; convert into a single map in which each key is a locale
  ;; and each value is a map of message IDs to translations for that locale
  (def locales-map
    (->> inbound-workbook
         ss/sheet-seq
         (map (juxt (comp keyword
                          ss/sheet-name)
                    (comp (partial into {})
                          (partial map (fn [kvp]
                                         (update kvp 0 deserialize-keyword)))
                          (partial map (juxt :message-id :translation))
                          sheet->maps)))
         (into {})))
  (def directory "tmp/translations")
  (let [dir-obj (clojure.java.io/file directory)]
    (when-not (.exists dir-obj)
      (.mkdir dir-obj))
    (doseq [[locale-name translations-map] locales-map]
      (spit (str directory "/" (coerce-locale-name locale-name) ".edn")
            (with-out-str (clojure.pprint/pprint translations-map)))))

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
  (def directory "tmp/translations")
  (def locale-files (some->> directory
                             clojure.java.io/file
                             file-seq
                             (filter (fn [file]
                                       (= "edn"
                                          (get-file-type file))))))
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
      (string? t) t
      :else (str t)))
  ;; convert into a seq of maps with locale-name, translations
  (defn- transform-for-docjure [locale]
    ((juxt :locale-name :translations)
     (-> locale
         (update :locale-name coerce-locale-name)
         (update :translations (fn [translations]
                                 (maps->table
                                  (map (fn [[message-id translation]]
                                         {"message ID"  (coerce-translation message-id)
                                          "translation" (coerce-translation translation)})
                                       translations)))))))
  (def locales (->> locale-files
                    (map (fn [file]
                           (let [locale-name  (keyword (get-file-name file))
                                 translations (->> file
                                                   slurp
                                                   clojure.edn/read-string)]
                             {:locale-name  locale-name
                              :translations translations})))))
  (def docjure-args (mapcat identity (map transform-for-docjure locales)))
  ;; create a new workbook
  ;; each locale name becomes a sheet name
  ;; each translation becomes a row
  ;; (maps -> table)
  (def outbound-workbook (apply ss/create-workbook docjure-args))
  (ss/save-workbook! "tmp/translations.xlsx" outbound-workbook)
  )
