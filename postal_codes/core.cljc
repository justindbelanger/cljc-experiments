(ns postal-codes.core)

(comment
  (require '[clojure.spec.alpha :as s])
  (require '[clojure.spec.gen.alpha :as gen])
  (def postal-code? #(re-matches #"[A-Z][0-9][A-Z]\s[0-9][A-Z][0-9]" %))
  (def digit-gen (gen/choose 0 9))
  (def upper-gen (gen/fmap clojure.core/char (gen/choose 65 90)))
  (s/def ::postal-code-3
    (s/with-gen
      #(re-matches #"[A-Z][0-9][A-Z]\s[0-9][A-Z][0-9]" %)
      #(gen/fmap
        (fn [chars]
          (apply str (concat (take 3 chars)
                             " "
                             (drop 3 chars))))
        (gen/tuple upper-gen digit-gen upper-gen
                   digit-gen upper-gen digit-gen))))
  (gen/sample (s/gen ::postal-code-3))
  ;; => ("J4S 2D1")
  )
