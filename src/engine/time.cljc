(ns engine.time 
  (:require
   [clojure.spec.alpha :as s]
   [odoyle.rules :as o]))

(defn insert [session total delta]
  (o/insert session ::now {::total total ::delta delta}))

;; specs
(s/def ::total number?)
(s/def ::delta number?)