(ns translations.util)

;; * Locales

(defn coerce-locale-name
  [locale-name]
  (cond
    (keyword? locale-name) (name locale-name)
    (string? locale-name)  locale-name
    :else                  (throw (ex-info "Invalid locale name"
                                           {:locale-name locale-name}))))

(defn sorted-translations-map
  "Takes an unsorted map of message IDs to translated strings,
  provides a sorted map with the message IDs in ascending order (a -> z).
  Makes `git diff`s of each EDN file easier."
  [translations-map]
  (->> translations-map
       (mapcat identity)
       (apply sorted-map-by (fn [k1 k2]
                              (compare (str k1) (str k2))))))
