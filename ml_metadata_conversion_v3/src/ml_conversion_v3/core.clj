(ns ml-conversion-v3.core
  (:require [libpython-clj.python :refer [py. py.. py.-] :as py]
            [clojure.java.io]))

(comment
  ;; config
  ;; used pyenv to install python 3.7.6
  ;; AND had to use '--enable-shared' to generate the 'libpython' shared library
  ;; that is,
  ;; 'PYTHON_CONFIGURE_OPTS="--enable-shared" pyenv install 3.7.6'
  (py/initialize! :python-executable "/home/jdb/.pyenv/versions/3.7.6/bin/python3"
                  :library-path "/home/jdb/.pyenv/versions/3.7.6/lib/libpython3.7m.so")

  ;; import pickle
  (require '[libpython-clj.require :refer [require-python]])
  (require-python '[builtins :as python])
  (require-python '[pickle])

  ;; load model pickle
  (def file-path "tmp/model_pickle") ;; file removed for privacy
  (def model-pickle (pickle/load (python/open file-path "rb")))
  (first model-pickle)
  ;; => ... lots of stuff ...
  )
