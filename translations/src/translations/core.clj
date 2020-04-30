(ns translations.core
  (:require [translations.edn-to-excel]
            [translations.excel-to-edn]))

;; republishing functions from separate namespaces

(def convert-excel-to-edn translations.excel-to-edn/convert-excel-to-edn)

(def convert-edn-to-excel translations.edn-to-excel/convert-edn-to-excel)
