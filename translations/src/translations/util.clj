(ns translations.util)

;; * Locales

(defn coerce-locale-name
  [locale-name]
  (cond
    (keyword? locale-name) (name locale-name)
    (string? locale-name)  locale-name
    :else                  (throw (ex-info "Invalid locale name"
                                           {:locale-name locale-name}))))
